@ECHO OFF
REM XMage Accessible Launcher
REM Shows a checkbox to enable screen reader support, then launches XMage.
REM Place this file in the same folder as XMageLauncher (the mage root folder).

SETLOCAL

SET AGENT_JAR=xmage\mage-client\lib\xmage-access-0.1.0.jar

IF NOT EXIST "%AGENT_JAR%" (
    ECHO.
    ECHO ERROR: xmage-access-0.1.0.jar not found.
    ECHO Expected at: %AGENT_JAR%
    ECHO.
    ECHO Please copy xmage-access-0.1.0.jar into the xmage\mage-client\lib\ folder.
    ECHO.
    PAUSE
    EXIT /B 1
)

java -Djava.net.preferIPv4Stack=true -cp "%AGENT_JAR%" xmageaccess.launcher.AccessibleLauncher
