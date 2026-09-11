@echo off
REM Pramaan Next startup helper (Windows wrapper).
REM Runs start.sh with Git Bash. Requires Git for Windows.
REM
REM Demo mode: start.sh prompts "Enable demo mode? [y/N]" after the D/L/R choice
REM and, if enabled, auto-seeds demo evidence. To skip the prompt, set it first:
REM     set DEMO_MODE=true  &  start.cmd      (this env var is passed through to bash)
setlocal
set "HERE=%~dp0"

where bash >nul 2>&1
if %ERRORLEVEL%==0 (
  bash "%HERE%start.sh"
  goto :eof
)

for %%P in (
  "%ProgramFiles%\Git\bin\bash.exe"
  "%ProgramFiles(x86)%\Git\bin\bash.exe"
  "%LocalAppData%\Programs\Git\bin\bash.exe"
) do (
  if exist "%%~P" (
    "%%~P" "%HERE%start.sh"
    goto :eof
  )
)

echo Could not find Git Bash. Install Git for Windows, or run start.sh from a Git Bash shell.
exit /b 1
