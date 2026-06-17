@echo off
cd /d "%~dp0"
"C:\Program Files\Java\jdk-26.0.1\bin\java.exe" --module-path "C:\Program Files\Java\javafx-sdk-21.0.11\lib" --add-modules javafx.controls,javafx.fxml -cp "payroll-app-1.0-SNAPSHOT.jar;libs\*" com.payroll.Main
pause