@echo off
echo ===================================================
echo  Starting AI Vehicle Parking Management Backend
echo ===================================================
javac ParkingServer.java
if %errorlevel% neq 0 (
    echo Error compiling ParkingServer.java! Please check JDK installation.
    pause
    exit /b %errorlevel%
)
java ParkingServer
pause
