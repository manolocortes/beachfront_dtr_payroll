package com.payroll.service;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.io.font.constants.StandardFonts;
import com.payroll.model.DayAttendance;
import com.payroll.model.Payroll;
import com.payroll.model.PayrollEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fills the "DAILY TIME RECORD AND PAYROLL" DTR template PDF.
 *
 * ── Page geometry ────────────────────────────────────────────────────────────
 * Template is stored as media 612x936 pts portrait with /Rotate 270.
 * Displayed as landscape: 936 wide x 612 tall.
 *
 * ── Coordinate systems ───────────────────────────────────────────────────────
 * "Display space" = what the user sees: origin top-left, x->right, y->down.
 *   Range: x 0..936, y 0..612
 *
 * iText PdfCanvas for this page uses raw media space with Y-axis FLIPPED
 * (ty=0 is at media_y=PAGE_MEDIA_H=936; ty increases upward toward media_y=0).
 *
 * Conversion  display(dx, dy)  ->  iText canvas(tx, ty):
 *   tx = PAGE_MEDIA_W(612) - dy      selects the horizontal band (row)
 *   ty = PAGE_MEDIA_H(936) - dx      selects the position along the band (column)
 *
 * ── Text placement ───────────────────────────────────────────────────────────
 * setTextMatrix(0, -1, 1, 0, tx, ty)
 *   Rotates glyphs 90 deg CW in canvas space -> appear upright in viewer.
 *   Advances in the -ty direction -> increasing display_x -> left-to-right.
 *
 * ── Line / path drawing ──────────────────────────────────────────────────────
 * PdfCanvas.moveTo / lineTo use raw media coords directly (no Y-flip).
 * Media coords for a display point: mx = PAGE_MEDIA_W - dy,  my = dx
 */
public class PayrollPdfService {

    // ── Page constants ────────────────────────────────────────────────────────
    private static final float PAGE_MEDIA_W = 612f;  // media width  (= display height)
    private static final float PAGE_MEDIA_H = 936f;  // media height (= display width)

    private static final DateTimeFormatter DATE_FMT    = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DAY_DATE_FMT = DateTimeFormatter.ofPattern("MM/dd");
    private static final String[] DAY_NAMES = {"MON","TUE","WED","THU","FRI","SAT","SUN"};
    private static final String TEMPLATE_RES = "/pdf/DTR_template.pdf";

    // ── Day sub-column X centers in display space ─────────────────────────────
    // 7 days x 3 slots (AM center, PM center, OT center)
    private static final float[][] DAY_COL_X = {
            {182.5f, 202.0f, 222.0f},  // Day 1
            {241.5f, 261.0f, 281.0f},  // Day 2
            {300.5f, 320.0f, 340.0f},  // Day 3
            {359.5f, 379.0f, 399.0f},  // Day 4
            {418.5f, 438.0f, 458.0f},  // Day 5
            {477.5f, 497.0f, 517.0f},  // Day 6
            {536.5f, 556.0f, 576.0f},  // Day 7
    };

    // ── Cell boundary arrays for diagonal lines ───────────────────────────────
    // 22 column boundaries for the 21 AM/PM/OT sub-columns (display x)
    private static final float[] COL_X = {
            173,192,212,232,251,271,291,310,330,350,369,389,409,428,448,468,487,507,527,546,566,586
    };
    // 23 row boundaries for the 22 data rows (display y)
    private static final float[] ROW_TOP = {
            118,138,157,176,195,214,233,253,272,291,310,329,349,368,387,406,425,444,464,483,502,521,540
    };
    private static final float INSET = 1.5f;  // margin inside each cell for the diagonal line
    private static final float VCENTER = 2.6f; // baseline offset to vertically center text within a data row

    // ── Summary column X positions in display space ───────────────────────────
    private static final float X_TOTAL_DAYS = 603.0f;
    private static final float X_TOTAL_OT   = 639.0f;
    private static final float X_RATE       = 659.0f;
    private static final float X_AMOUNT      = 729.4f;   // center of AMOUNT column (691.7..767.0)
    private static final float X_AMOUNT_RIGHT = 764.0f;  // right boundary of AMOUNT col minus 3pt padding

    // Horizontal absence line inner padding (each side)
    private static final float LINE_INSET = 3.0f;

    // ── Data row Y centers in display space (22 rows) ─────────────────────────
    private static final float[] ROW_Y = {
            128.0f, 147.5f, 166.5f, 185.5f, 204.5f, 223.5f, 243.0f, 262.5f,
            281.5f, 300.5f, 319.5f, 339.0f, 358.5f, 377.5f, 396.5f, 415.5f,
            434.5f, 454.0f, 473.5f, 492.5f, 511.5f, 530.5f
    };

    // ── Day header row Y (above AM/PM/OT column groups) ──────────────────────
    private static final float Y_DAY_HEADER       = 96.0f;  // display y for day-date labels

    // ── Header/footer display coordinates ─────────────────────────────────────
    private static final float Y_HEADER           = 70.0f;
    private static final float X_PROJECT_VAL      = 100.0f;
    private static final float X_PERIOD_START     = 640.0f;
    private static final float X_PERIOD_TO        = 754.0f;
    private static final float Y_SHEET            = 35.0f;
    private static final float X_SHEET_NO_CENTER  = 896.5f;  // center of the SHEET NO: underline (872.8..923.8)
    private static final float X_SHEET_NO_COLW    = 51.0f;   // underline width for putC centering
    private static final float Y_FOOTER           = 587.0f;
    private static final float X_PREPARED_VAL     = 113.0f;  // underline 89.1..310.4
    private static final float X_CHECKED_VAL      = 727.9f;  // underline 703.7..925.0, same indent as PREPARED

    // ── Pagination ───────────────────────────────────────────────────────────
    private static final int DATA_ROWS_PER_PAGE = ROW_Y.length - 1; // 21 worker rows + 1 subtotal row per page

    // ── Public API ────────────────────────────────────────────────────────────

    public void generate(Payroll payroll, String outputPath, String templatePath) throws IOException {
        byte[] tpl = loadTemplate(templatePath);
        if (tpl == null)
            throw new IOException("DTR_template.pdf not found. Place it next to the app JAR.");

        List<PayrollEntry> entries = payroll.getEntries();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) DATA_ROWS_PER_PAGE));

        // Strategy: generate each page as an independent single-page PDF
        // (reader+writer on the template), collect bytes, then concatenate
        // the raw page content into a single output PDF.
        // We avoid copyPagesTo() entirely on the final merge because iText
        // wraps the page in a Form XObject which corrupts our coordinate transforms.
        // Instead we use PdfMerger which preserves the page content streams directly.

        if (totalPages == 1) {
            // Fast path: single page, write directly
            generateSinglePage(tpl, payroll, outputPath, 1, 1,
                    entries.isEmpty() ? entries : entries.subList(0, entries.size()));
            return;
        }

        // Multi-page: generate each page to a temp file, then merge with PdfMerger
        java.io.File tmpDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        java.util.List<java.io.File> tmpFiles = new java.util.ArrayList<>();
        try {
            for (int page = 0; page < totalPages; page++) {
                int start = page * DATA_ROWS_PER_PAGE;
                int end   = Math.min(start + DATA_ROWS_PER_PAGE, entries.size());
                java.io.File tmp = new java.io.File(tmpDir, "dtr_page_" + page + "_" + System.nanoTime() + ".pdf");
                generateSinglePage(tpl, payroll, tmp.getAbsolutePath(), page + 1, totalPages,
                        entries.subList(start, end));
                tmpFiles.add(tmp);
            }

            // Merge using PdfMerger — this copies pages as full page content, not XObjects
            com.itextpdf.kernel.utils.PdfMerger merger =
                    new com.itextpdf.kernel.utils.PdfMerger(new PdfDocument(new PdfWriter(outputPath)));
            for (java.io.File tmp : tmpFiles) {
                PdfDocument src = new PdfDocument(new PdfReader(tmp.getAbsolutePath()));
                merger.merge(src, 1, 1);
                src.close();
            }
            merger.close();

        } finally {
            for (java.io.File tmp : tmpFiles) {
                try { tmp.delete(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Generates one page of the DTR, writing directly into a PdfDocument
     * opened as reader+writer on the template. This is the original working
     * approach that produces correct coordinate transforms.
     */
    private void generateSinglePage(byte[] tpl, Payroll payroll, String outputPath,
                                    int pageNum, int totalPages,
                                    List<PayrollEntry> pageEntries) throws IOException {
        PdfDocument pdf = new PdfDocument(
                new PdfReader(new java.io.ByteArrayInputStream(tpl)),
                new PdfWriter(outputPath));
        PdfCanvas canvas = new PdfCanvas(pdf.getFirstPage());

        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);

        writeHeader(canvas, payroll, regular, pageNum, totalPages);
        writeRows(canvas, pageEntries, regular, bold);
        writeFooter(canvas, payroll, regular);

        canvas.release();
        pdf.close();
    }

    public void generate(Payroll payroll, String outputPath) throws IOException {
        generate(payroll, outputPath, null);
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private void writeHeader(PdfCanvas c, Payroll p, PdfFont f, int pageNum, int totalPages) {
        put(c, f, 10.5f, nvl(p.getProjectName()), X_PROJECT_VAL, Y_HEADER);
        if (p.getPeriodStart() != null)
            put(c, f, 10.5f, p.getPeriodStart().format(DATE_FMT), X_PERIOD_START, Y_HEADER);
        if (p.getPeriodEnd() != null)
            put(c, f, 10.5f, p.getPeriodEnd().format(DATE_FMT), X_PERIOD_TO, Y_HEADER);

        // Sheet No.: center on the underline for single page; show "X of Y" left-aligned for multi
        String sheetNo = totalPages > 1 ? (pageNum + " of " + totalPages) : String.valueOf(pageNum);
        if (totalPages == 1) {
            putC(c, f, 10.5f, sheetNo, X_SHEET_NO_CENTER, Y_SHEET, X_SHEET_NO_COLW);
        } else {
            put(c, f, 10.5f, sheetNo, 872.8f, Y_SHEET);
        }

        // Day-date labels above AM|PM|OT column groups
        writeDayHeaders(c, f, p.getPeriodStart());
    }

    /**
     * Write "MON(MM/DD)" labels centered above each 3-column day group.
     * The center X for each day group is the midpoint of its AM and OT sub-columns.
     * If periodStart is null, falls back to bare day names only.
     */
    /*private void writeDayHeaders(PdfCanvas c, PdfFont f, LocalDate periodStart) {
        // Day-of-week order: MON=0 … SUN=6
        DayOfWeek[] order = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY};

        for (int d = 0; d < 7; d++) {
            // Center X = midpoint of AM-center and OT-center for this day
            float centerX = (DAY_COL_X[d][0] + DAY_COL_X[d][2]) / 2f;
            // Total width spanning from left edge of AM cell to right edge of OT cell
            float groupWidth = COL_X[d * 3 + 3] - COL_X[d * 3];

            String label;
            if (periodStart != null) {
                // Find the date for this day-of-week slot within the week of periodStart
                // The period starts on whichever day periodStart falls; map slot d to that date
                LocalDate date = periodStart.plusDays(d);
                label = DAY_NAMES[d] + "(" + date.format(DAY_DATE_FMT) + ")";
            } else {
                label = DAY_NAMES[d];
            }

            putC(c, f, 10.5f, label, centerX, Y_DAY_HEADER, groupWidth);
        }
    }*/

    private void writeDayHeaders(PdfCanvas c, PdfFont f, LocalDate periodStart) {
        for (int d = 0; d < 7; d++) {
            // Center X = midpoint of AM-center and OT-center for this day
            float centerX = (DAY_COL_X[d][0] + DAY_COL_X[d][2]) / 2f;
            // Total width spanning from left edge of AM cell to right edge of OT cell
            float groupWidth = COL_X[d * 3 + 3] - COL_X[d * 3];

            String label;
            if (periodStart != null) {
                // Find the date for this dynamic day slot starting from periodStart
                LocalDate date = periodStart.plusDays(d);

                // date.getDayOfWeek().getValue() returns 1 (MON) to 7 (SUN)
                int dayOfWeekIndex = date.getDayOfWeek().getValue() - 1;

                label = DAY_NAMES[dayOfWeekIndex] + "(" + date.format(DAY_DATE_FMT) + ")";
            } else {
                label = DAY_NAMES[d]; // Fallback if no period restriction is provided
            }

            putC(c, f, 10.5f, label, centerX, Y_DAY_HEADER, groupWidth);
        }
    }

    private void writeRows(PdfCanvas c, List<PayrollEntry> pageEntries, PdfFont reg, PdfFont bold) {
        int limit = Math.min(pageEntries.size(), DATA_ROWS_PER_PAGE);

        for (int i = 0; i < limit; i++) {
            PayrollEntry e = pageEntries.get(i);
            float y = ROW_Y[i] + VCENTER;

            // Worker name: numbered "1. Name of worker" (left-aligned)
            String numberedName = truncate((i + 1) + ". " + nvl(e.getWorkerName()), 26);
            put(c, reg, 10.5f, numberedName, 12f, y);

            // Per-day attendance: diagonal line for AM/PM, number for OT; horizontal line for absent slots
            List<DayAttendance> days = e.getDayAttendance();

            // First pass: draw diagonal/OT marks
            for (int d = 0; d < 7 && d < days.size(); d++) {
                DayAttendance da = days.get(d);
                boolean am = da.isAm();
                boolean pm = da.isPm();
                boolean ot = da.getOtHours() > 0;

                if (am) drawDiag(c, d, 0, i);
                if (pm) drawDiag(c, d, 1, i);
                if (ot) putC(c, reg, 10.5f, fmtN(da.getOtHours()), DAY_COL_X[d][2], y, 19f);
            }

            // Second pass: draw horizontal absence lines
            // For fully-absent days (no AM, no PM, no OT), collect consecutive runs and draw one merged line.
            // For partial absence (present on some sub-columns), draw a line just in the missing sub-column(s).
            int d = 0;
            int dayCount = Math.min(7, days.size());
            while (d < dayCount) {
                DayAttendance da = days.get(d);
                boolean am = da.isAm();
                boolean pm = da.isPm();
                boolean ot = da.getOtHours() > 0;
                boolean fullyAbsent = !am && !pm && !ot;

                if (fullyAbsent) {
                    // Draw one line spanning AM+PM+OT for this day only, with padding
                    float lineX0 = COL_X[d * 3]     + LINE_INSET;
                    float lineX1 = COL_X[d * 3 + 3] - LINE_INSET;
                    drawHorizLine(c, lineX0, lineX1, i);
                    d++;
                } else {
                    // Partial absence: draw line in each missing sub-column with padding
                    if (!am) drawHorizLine(c, COL_X[d * 3]     + LINE_INSET, COL_X[d * 3 + 1] - LINE_INSET, i);
                    if (!pm) drawHorizLine(c, COL_X[d * 3 + 1] + LINE_INSET, COL_X[d * 3 + 2] - LINE_INSET, i);
                    if (!ot) drawHorizLine(c, COL_X[d * 3 + 2] + LINE_INSET, COL_X[d * 3 + 3] - LINE_INSET, i);
                    d++;
                }
            }

            // Summary columns
            putC(c, reg, 10.5f, fmtN(e.getDaysWorked()),    X_TOTAL_DAYS, y, 34f);
            putC(c, reg, 10.5f, fmtN(e.getOvertimeHours()), X_TOTAL_OT,   y, 34f);
            put(c, reg, 10.5f, fmtM(e.getDailyRate()),   X_RATE,       y);
            // Amount: right-aligned within its column
            putR(c, bold, 10.5f, fmtM(e.getNetPay()), X_AMOUNT_RIGHT, y);
        }

        // Subtotal row for this page
        if (limit < ROW_Y.length) {
            float ty = ROW_Y[limit] + VCENTER;
            double tDays = pageEntries.stream().mapToDouble(PayrollEntry::getDaysWorked).sum();
            double tOT   = pageEntries.stream().mapToDouble(PayrollEntry::getOvertimeHours).sum();
            double tNet  = pageEntries.stream().mapToDouble(PayrollEntry::getNetPay).sum();
            put(c, bold, 10.5f,  "TOTAL",        12f,          ty);
            //putC(c, bold, 10.5f, fmtN(tDays),   X_TOTAL_DAYS, ty, 34f);
            //putC(c, bold, 10.5f, fmtN(tOT),     X_TOTAL_OT,   ty, 34f);
            putR(c, bold, 10.5f, fmtM(tNet),    X_AMOUNT_RIGHT, ty);
        }
    }

    private void writeFooter(PdfCanvas c, Payroll p, PdfFont f) {
        put(c, f, 10.5f, nvl(p.getPreparedBy()), X_PREPARED_VAL, Y_FOOTER);
        put(c, f, 10.5f, nvl(p.getApprovedBy()), X_CHECKED_VAL,  Y_FOOTER);
    }

    // ── Text drawing ──────────────────────────────────────────────────────────

    /**
     * Place text at display coord (dx, dy).
     *
     * Conversion to iText canvas:
     *   tx = PAGE_MEDIA_W - dy   (612 - display_y)
     *   ty = PAGE_MEDIA_H - dx   (936 - display_x)
     *
     * Text matrix (0, -1, 1, 0) rotates glyphs 90 deg CW; advances in -ty
     * direction = increasing display_x = left-to-right in the viewer.
     */
    private void put(PdfCanvas c, PdfFont font, float size, String txt, float dx, float dy) {
        if (txt == null || txt.isEmpty()) return;
        float tx = PAGE_MEDIA_W - dy;   // 612 - display_y
        float ty = PAGE_MEDIA_H - dx;   // 936 - display_x
        c.beginText()
                .setFontAndSize(font, size)
                .setTextMatrix(0, -1, 1, 0, tx, ty)
                .showText(txt)
                .endText();
    }

    /** Centre text horizontally around centerDx within a cell of colWidth. */
    private void putC(PdfCanvas c, PdfFont font, float size,
                      String txt, float centerDx, float dy, float colWidth) {
        if (txt == null || txt.isEmpty()) return;
        float w = font.getWidth(txt, size);
        put(c, font, size, txt, centerDx - w / 2f, dy);
    }

    /**
     * Right-align text so its right edge sits at rightEdgeDx.
     * Used for amount/currency columns.
     */
    private void putR(PdfCanvas c, PdfFont font, float size,
                      String txt, float rightEdgeDx, float dy) {
        if (txt == null || txt.isEmpty()) return;
        float w = font.getWidth(txt, size);
        put(c, font, size, txt, rightEdgeDx - w, dy);
    }

    // ── Diagonal line drawing ─────────────────────────────────────────────────

    /**
     * Draw a full diagonal line filling one AM or PM cell.
     *
     * Parameters:
     *   day  = 0..6  (Day 1 = 0)
     *   slot = 0 = AM, 1 = PM
     *   row  = 0..21 (data row index)
     *
     * The line runs from the top-left corner to the bottom-right corner
     * of the cell, inset by INSET pts on all sides.
     *
     * moveTo/lineTo use the SAME coordinate transform as put() — iText applies
     * the same Y-axis flip to path operators as it does to text matrices on
     * this /Rotate 270 page:
     *   media_x = PAGE_MEDIA_W − display_y
     *   media_y = PAGE_MEDIA_H − display_x
     */
    private void drawDiag(PdfCanvas c, int day, int slot, int row) {
        int col = day * 3 + slot;

        // Display-space corners of this cell (with inset)
        float dx0 = COL_X[col]       + INSET;   // left  edge  (display x)
        float dx1 = COL_X[col + 1]   - INSET;   // right edge  (display x)
        float dy0 = ROW_TOP[row]     + INSET;   // top   edge  (display y)
        float dy1 = ROW_TOP[row + 1] - INSET;   // bottom edge (display y)

        // Convert to media coords (same transform as put())
        float mx0 = PAGE_MEDIA_W - dy0;   // 612 - display_y
        float my0 = PAGE_MEDIA_H - dx0;   // 936 - display_x
        float mx1 = PAGE_MEDIA_W - dy1;
        float my1 = PAGE_MEDIA_H - dx1;

        c.setLineWidth(0.9f)
                .moveTo(mx0, my0)
                .lineTo(mx1, my1)
                .stroke();
    }

    /**
     * Draw a horizontal line in display space spanning dx0→dx1 at the vertical
     * midpoint of data row {@code row}.  Converts to media coordinates using the
     * same 90° rotation as put() / drawDiag().
     *
     * In display space a "horizontal" line has constant dy and varying dx, which
     * maps to constant mx and varying my in media space:
     *   mx  = PAGE_MEDIA_W − dy_mid        (fixed for the row)
     *   my0 = PAGE_MEDIA_H − dx0           (left  end in display → higher my)
     *   my1 = PAGE_MEDIA_H − dx1           (right end in display → lower  my)
     */
    private void drawHorizLine(PdfCanvas c, float dx0, float dx1, int row) {
        float dyTop = ROW_TOP[row];
        float dyBot = ROW_TOP[row + 1];
        float dyMid = (dyTop + dyBot) / 2f;   // vertical midpoint of the row in display space

        float mx  = PAGE_MEDIA_W - dyMid;     // fixed media-x for this row
        float my0 = PAGE_MEDIA_H - dx0;       // media-y for left  end of line
        float my1 = PAGE_MEDIA_H - dx1;       // media-y for right end of line

        c.setLineWidth(0.9f)
                .moveTo(mx, my0)
                .lineTo(mx, my1)
                .stroke();
    }

    // ── Template loading ──────────────────────────────────────────────────────

    private byte[] loadTemplate(String path) throws IOException {
        if (path != null && Files.exists(Paths.get(path)))
            return Files.readAllBytes(Paths.get(path));
        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_RES)) {
            if (is != null) return is.readAllBytes();
        }
        java.io.File local = new java.io.File("DTR_template.pdf");
        if (local.exists()) return Files.readAllBytes(local.toPath());
        return null;
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private String fmtN(double v) {
        if (v == 0) return "";
        return (v == Math.floor(v)) ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    private String fmtM(double v) {
        return v == 0 ? "" : String.format("%,.2f", v);
    }

    private String nvl(String s)           { return s != null ? s : ""; }
    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "~" : s;
    }
}