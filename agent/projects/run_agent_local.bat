@echo off
setlocal enabledelayedexpansion

set ROOT=C:\Users\Lenovo\Desktop\demo\test-program\AI-shop
set PROJ=%ROOT%\agent\projects
set COZE_WORKSPACE_PATH=%PROJ%
set SPRING_BOOT_API_URL=http://127.0.0.1:8080/api/agent
set OPENAI_API_BASE=https://sub.jlypx.de/v1
set OPENAI_API_KEY=sk-cd007684f18014699531936e7adc9725c51977350584d623ea5797ec92bb69f7
set MODEL_NAME=gpt-5.4

cd /d %PROJ%
%PROJ%\.venv\Scripts\python.exe %PROJ%\src\main.py -m http -p 5000
