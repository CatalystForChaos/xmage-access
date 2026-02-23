@ECHO OFF
REM XMage Client with Accessibility Agent
REM This script launches XMage with screen reader support (NVDA, JAWS, or SAPI).
REM Place xmage-access-0.1.0.jar in the lib\ folder next to mage-client.

SETLOCAL

REM Find the agent JAR
SET AGENT_JAR=%~dp0lib\xmage-access-0.1.0.jar

IF NOT EXIST "%AGENT_JAR%" (
    ECHO.
    ECHO ERROR: Accessibility agent not found.
    ECHO Expected location: %AGENT_JAR%
    ECHO.
    ECHO Please copy xmage-access-0.1.0.jar into the lib folder.
    ECHO.
    PAUSE
    EXIT /B 1
)

ECHO Starting XMage with accessibility support...
java -Xmx2000m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Djava.net.preferIPv4Stack=true -javaagent:"%AGENT_JAR%" -jar .\lib\mage-client-1.4.58.jar
