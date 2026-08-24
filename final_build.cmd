@echo off
cd /d c:\Users\leona\Desktop\FreeTime_project\FreeTime\SecureChatApp
call gradlew.bat build 2>&1 | findstr "BUILD" > final_build_status.txt
if exist final_build_status.txt (
    type final_build_status.txt
) else (
    echo No build status found
)
