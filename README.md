# AI-Powered Vehicle Parking Management System (Java Fullstack)

A modern fullstack web application designed to manage smart parking facilities, vehicles, parking slots, entry/exit tickets, fee calculation, payments, customer slot reservations, role permissions, reports, and AI-powered analytics.

All project files are organized in a single folder for seamless execution.

---

## 📁 File Structure

```text
vehicleparking/
├── ParkingServer.java   # Pure Java Backend HTTP Server & REST API endpoints
├── index.html           # Full Dashboard Web Interface & Navigational Menus
├── style.css            # Dark/Light Modern SaaS Styling & Visual Slot Layout
├── app.js               # Frontend Application Logic, API calls & Chart.js Integration
├── run.bat              # Windows 1-Click Launch Script
└── README.md            # Documentation & Usage Guide
```

---

## 🚀 How to Run the Application

### Option 1: Quick Launch (Windows Batch File)
Double click on `run.bat` or run in terminal:
```cmd
run.bat
```

### Option 2: Manual Terminal Execution
Open terminal inside `c:\Users\maham\Desktop\fullstack\vehicleparking` and run:

1. **Compile Backend**:
   ```cmd
   javac ParkingServer.java
   ```

2. **Run Backend Server**:
   ```cmd
   java ParkingServer
   ```

3. **Access Web Application**:
   Open your browser and navigate to:
   **[http://localhost:8080](http://localhost:8080)**

---

## 🌟 Key Features & Navigational Menus

1. **📊 Dashboard**:
   - Live stat cards: Total Slots, Available Slots, Occupied Slots, Today's Revenue.
   - Dynamic Charts: Hourly Traffic (Line Chart) and Slot Occupancy Breakdown (Doughnut Chart).
   - Currently Parked Vehicles list with quick exit action.

2. **🅿️ Parking Slots Layout**:
   - Interactive visual parking grid across Ground Floor, 1st Floor, Basement 1, VIP Zone, and Two-Wheeler Bay.
   - Color coded statuses (`AVAILABLE`, `OCCUPIED`, `RESERVED`, `MAINTENANCE`).

3. **📥 Vehicle Entry**:
   - Vehicle registration & slot assignment.
   - **AI Smart Slot Recommendation**: Click to let AI choose the optimal slot.
   - **AI ANPR Camera Scanner Simulation**: Automatic license plate reading.
   - Generates digital printable ticket with QR code (`PKT-YYYYMMDD-XXXXXX`).

4. **📤 Vehicle Exit & Fee Calculation**:
   - Instant ticket / plate number lookup.
   - Server-side fee calculation based on duration, hourly rates, grace period (15 mins), and tax (18%).
   - Multi-payment support (UPI, Cash, Card, Net Banking, Wallet).
   - Slot release & digital receipt generation.

5. **🚗 Vehicles Management**:
   - Search, filter, and register vehicles.

6. **📅 Reservations**:
   - Book parking slots in advance for specific dates/times.
   - Slot conflict detection.

7. **💳 Payments & Billing**:
   - Detailed transaction log history with status badges.

8. **🤖 AI Dashboard**:
   - **ANPR OCR Model**: 98.4% confidence plate recognition.
   - **Demand Prediction**: 24-hour occupancy forecast model.
   - **Revenue Forecast**: 7-day projected revenue.
   - **Suspicious Activity Alert Feed**: Real-time security flagging overstaying/mismatched vehicles.

9. **📈 Reports**:
   - Financial & operational daily/monthly summaries with CSV export.

10. **👥 User Management**:
    - Manage Admin, Manager, Staff, Security, and Customer roles.

11. **⚙️ Settings**:
    - Configurable tax percentage, grace period, facility name, currency symbol.

12. **📜 Audit Logs**:
    - Real-time audit trail of all administrative and financial actions.


## 🔐 Registration & Login
The application does **not** use hard-coded email/password demo accounts.

### First-time user
1. Open the login page.
2. Click **Create an account**.
3. Enter your full name, email, phone, role, and your own password.
4. Click **Register Account**.
5. Return to Login.

### Login
1. Select **Admin, Manager, Staff, Security, or Customer**.
2. Enter the **same email and password you registered**.
3. Click **Login**.
4. The backend verifies the credentials **and selected role** before opening the dashboard.

Selecting a role never fills a default email/password and never navigates directly to the dashboard.
Registered users are stored locally in `users.db`, so their accounts remain available after restarting the Java server. Passwords are stored as SHA-256 hashes rather than plain text.

> **Important:** `users.db` is created automatically after the first successful registration. Do not delete it if you want to keep registered accounts.

The login screen remains dark and the dashboard uses the professional readable theme from the updated version.
