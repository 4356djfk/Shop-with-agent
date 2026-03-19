@echo off
setlocal enabledelayedexpansion

set ROOT=C:\Users\Lenovo\Desktop\demo\test-program\AI-shop
set PROJ=%ROOT%\agent\projects
set COZE_WORKSPACE_PATH=%PROJ%
set SPRING_BOOT_API_URL=http://127.0.0.1:8080/api/agent
set OPENAI_API_BASE=https://api.190269.xyz/v1
set OPENAI_API_KEY=sk-GD56LBULM2EBizeRbsRopXzEBM0X2XH1QuS5X9JTJOVTpfCv
set MODEL_NAME=gpt-5.2

cd /d %PROJ%
%PROJ%\.venv\Scripts\python.exe %PROJ%\src\main.py -m http -p 5000
