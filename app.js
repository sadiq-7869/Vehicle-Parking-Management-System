/**
 * AI-Powered Vehicle Parking Management System - Frontend App Logic
 * Pure Black Theme & Login Authentication Logic
 */

const API_BASE = "http://localhost:8080/api";

let currentRole = "ADMIN";
let currentUser = null;
let currentSlotsData = [];
let currentActiveSessions = [];
let trafficChartInstance = null;
let occupancyChartInstance = null;
let aiDemandChartInstance = null;

// Initialize on DOM load
document.addEventListener("DOMContentLoaded", () => {
    checkExistingSession();
    initUI();

    // Toggle Sidebar Mobile
    document.getElementById("menu-toggle").addEventListener("click", () => {
        document.getElementById("wrapper").classList.toggle("toggled");
    });

    // Auto refresh every 15s when logged in
    setInterval(() => {
        if (currentUser && !document.getElementById("view-dashboard").classList.contains("d-none")) {
            loadDashboardData();
        }
    }, 15000);
});

// -------------------------------------------------------------------
// Authentication & Session Management
// -------------------------------------------------------------------
function checkExistingSession() {
    const savedUserStr = sessionStorage.getItem("smartpark_user");
    if (savedUserStr) {
        try {
            currentUser = JSON.parse(savedUserStr);
            currentRole = currentUser.role || "ADMIN";
            showAppWrapper();
        } catch (e) {
            showLoginWrapper();
        }
    } else {
        showLoginWrapper();
    }
}

function showLoginWrapper() {
    document.body.classList.remove("dashboard-mode");
    document.getElementById("view-login").classList.remove("d-none");
    document.getElementById("wrapper").classList.add("d-none");
}

function showAppWrapper() {
    document.body.classList.add("dashboard-mode");
    document.getElementById("view-login").classList.add("d-none");
    document.getElementById("wrapper").classList.remove("d-none");

    document.getElementById("user-display-role").textContent = currentRole;
    updateRoleDashboard();
    document.getElementById("user-display-name").textContent = currentUser.full_name || "User";

    enforceRolePermissions();
    loadDashboardData();
}

async function handleLogin(e) {
    e.preventDefault();
    const emailInput = document.getElementById("login-email");
    const passwordInput = document.getElementById("login-password");
    const errorBox = document.getElementById("login-error-msg");
    const submitButton = document.querySelector("#form-login button[type=\"submit\"]");
    const email = emailInput.value.trim().toLowerCase();
    const password = passwordInput.value;

    errorBox.classList.add("d-none");
    if (!currentLoginRole) {
        errorBox.textContent = "Please select your role first.";
        errorBox.classList.remove("d-none");
        return;
    }
    if (!email || !password) {
        errorBox.textContent = "Enter the email and password you used during registration.";
        errorBox.classList.remove("d-none");
        return;
    }

    submitButton.disabled = true;
    submitButton.innerHTML = '<i class="fa-solid fa-spinner fa-spin me-2"></i>Signing In...';
    try {
        const res = await fetch(`${API_BASE}/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password, role: currentLoginRole })
        });
        const result = await res.json();
        if (!res.ok || !result.success) throw new Error(result.message || "Invalid login details.");
        currentUser = result.user;
        currentRole = currentUser.role || currentLoginRole;
        sessionStorage.setItem("smartpark_user", JSON.stringify(currentUser));
        showAppWrapper();
    } catch (err) {
        errorBox.textContent = err.message || "Unable to sign in.";
        errorBox.classList.remove("d-none");
        passwordInput.focus();
    } finally {
        submitButton.disabled = false;
        submitButton.innerHTML = '<i class="fa-solid fa-right-to-bracket me-2"></i>Login';
    }
}

let currentLoginRole = "";

function selectLoginRole(role) {
    currentLoginRole = role;
    document.querySelectorAll(".role-login-card").forEach(card => card.classList.remove("selected"));
    const selectedCard = document.querySelector(`.role-login-card[data-role="${role}"]`);
    if (selectedCard) selectedCard.classList.add("selected");
    const label = document.getElementById("selected-role-label");
    if (label) label.innerHTML = `<i class="fa-solid fa-circle-check me-1"></i><strong>${role.charAt(0) + role.slice(1).toLowerCase()}</strong> selected. Enter the email and password you registered for this role.`;
    document.getElementById("login-error-msg").classList.add("d-none");
    document.getElementById("login-email").focus();
}

function showRegisterForm() {
    document.getElementById("register-panel").classList.remove("d-none");
    document.getElementById("form-login").classList.add("d-none");
    document.querySelector(".quick-login-section").classList.add("d-none");
    document.getElementById("login-error-msg").classList.add("d-none");
    document.querySelector("#view-login .text-center.mt-4").classList.add("d-none");
}

function hideRegisterForm() {
    document.getElementById("register-panel").classList.add("d-none");
    document.getElementById("form-login").classList.remove("d-none");
    document.querySelector(".quick-login-section").classList.remove("d-none");
    document.querySelector("#view-login .text-center.mt-4").classList.remove("d-none");
}

async function handleRegister(e) {
    e.preventDefault();
    const errorBox = document.getElementById("login-error-msg");
    const successBox = document.getElementById("login-success-msg");
    errorBox.classList.add("d-none");
    successBox.classList.add("d-none");
    const name = document.getElementById("register-name").value.trim();
    const email = document.getElementById("register-email").value.trim().toLowerCase();
    const phone = document.getElementById("register-phone").value.trim();
    const role = document.getElementById("register-role").value;
    const password = document.getElementById("register-password").value;
    const confirm = document.getElementById("register-confirm-password").value;
    if (password !== confirm) {
        errorBox.textContent = "Passwords do not match.";
        errorBox.classList.remove("d-none");
        return;
    }
    try {
        const res = await fetch(`${API_BASE}/users`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ full_name: name, email, phone, role, password })
        });
        const result = await res.json();
        if (!res.ok || !result.success) throw new Error(result.message || "Registration failed.");
        document.getElementById("form-register").reset();
        hideRegisterForm();
        selectLoginRole(role);
        document.getElementById("login-email").value = email;
        document.getElementById("login-password").value = "";
        successBox.textContent = "Registration successful. Now enter your registered password to login.";
        successBox.classList.remove("d-none");
    } catch (err) {
        errorBox.textContent = err.message || "Registration failed.";
        errorBox.classList.remove("d-none");
    }
}

function handleLogout() {
    sessionStorage.removeItem("smartpark_user");
    currentUser = null;
    showLoginWrapper();
}

function initUI() {
    updateTimeDisplays();
    setInterval(updateTimeDisplays, 1000);
}

function updateTimeDisplays() {
    const now = new Date().toLocaleString();
    const entryDisp = document.getElementById("entry-time-display");
    if (entryDisp) entryDisp.value = now;
}

// -------------------------------------------------------------------
// Navigation & View Routing
// -------------------------------------------------------------------
function switchNav(viewId, event) {
    if (event) event.preventDefault();
    if (currentUser && !isViewAllowed(viewId)) {
        showAccessDenied(viewId);
        return;
    }

    document.querySelectorAll(".content-view").forEach(v => v.classList.add("d-none"));
    const target = document.getElementById(`view-${viewId}`);
    if (target) target.classList.remove("d-none");

    document.querySelectorAll("#main-nav a").forEach(a => a.classList.remove("active"));
    const activeLink = document.querySelector(`#main-nav a[href="#${viewId}"]`);
    if (activeLink) activeLink.classList.add("active");

    const titles = {
        'dashboard': 'Dashboard Overview',
        'parking-slots': 'Interactive Parking Slot Layout',
        'parking-entry': 'Vehicle Entry Registration',
        'parking-exit': 'Vehicle Exit & Payment Calculation',
        'vehicles': 'Vehicle Database',
        'reservations': 'Customer Slot Reservations',
        'payments': 'Payments & Financial Transactions',
        'ai-dashboard': 'AI Analytics & Security Dashboard',
        'reports': 'System Reports & Analytics',
        'users': 'User & Staff Management',
        'settings': 'Facility Configuration',
        'audit-logs': 'Audit Logs & Action History'
    };
    document.getElementById("page-title").textContent = titles[viewId] || 'Parking Management';

    if (viewId === 'dashboard') loadDashboardData();
    else if (viewId === 'parking-slots') fetchSlotsAndRender('ALL');
    else if (viewId === 'parking-entry') prepareEntryForm();
    else if (viewId === 'vehicles') loadVehicles();
    else if (viewId === 'reservations') loadReservations();
    else if (viewId === 'payments') loadPayments();
    else if (viewId === 'ai-dashboard') loadAIDashboard();
    else if (viewId === 'users') loadUsers();
    else if (viewId === 'audit-logs') loadAuditLogs();
}

function updateRoleDashboard() {
    const roleInfo = {
        ADMIN: {
            title: "Welcome, Administrator",
            description: "Monitor the complete parking operation, users, revenue, configuration and security activity.",
            access: "Full administrative access",
            nav: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments','ai-dashboard','reports','users','settings','audit-logs']
        },
        MANAGER: {
            title: "Welcome, Manager",
            description: "Manage parking capacity, vehicle movement, reservations, payments, reports and operational performance.",
            access: "Manager operational access",
            nav: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments','ai-dashboard','reports']
        },
        STAFF: {
            title: "Welcome, Parking Staff",
            description: "Handle daily vehicle entry and exit, parking slots, vehicles, reservations and customer payments.",
            access: "Staff operational access",
            nav: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments']
        },
        SECURITY: {
            title: "Welcome, Security Officer",
            description: "Monitor occupied slots, vehicle activity, AI security alerts and audit activity.",
            access: "Security monitoring access",
            nav: ['dashboard','parking-slots','vehicles','ai-dashboard','audit-logs']
        },
        CUSTOMER: {
            title: "Welcome, Customer",
            description: "View parking availability, manage your vehicle, reserve a slot and review your payments.",
            access: "Customer self-service access",
            nav: ['dashboard','parking-slots','vehicles','reservations','payments']
        }
    };
    const info = roleInfo[currentRole] || roleInfo.CUSTOMER;
    const prettyRole = currentRole.charAt(0) + currentRole.slice(1).toLowerCase();
    const setText = (id, value) => { const el = document.getElementById(id); if (el) el.textContent = value; };
    setText("dashboard-role-label", `${prettyRole} Dashboard`);
    setText("dashboard-welcome-title", info.title);
    setText("dashboard-role-description", info.description);
    setText("dashboard-access-label", info.access);
    setText("header-role-lock", `${prettyRole} access locked`);

    enforceRolePermissions();

    // Hide dashboard actions that are not appropriate for the authenticated role.
    const newEntry = document.getElementById("dashboard-new-entry-btn");
    if (newEntry) newEntry.classList.toggle("d-none", !['ADMIN','MANAGER','STAFF'].includes(currentRole));
}

function enforceRolePermissions() {
    const permissions = {
        ADMIN: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments','ai-dashboard','reports','users','settings','audit-logs'],
        MANAGER: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments','ai-dashboard','reports'],
        STAFF: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments'],
        SECURITY: ['dashboard','parking-slots','vehicles','ai-dashboard','audit-logs'],
        CUSTOMER: ['dashboard','parking-slots','vehicles','reservations','payments']
    };
    const allowed = permissions[currentRole] || permissions.CUSTOMER;
    document.querySelectorAll("#main-nav a").forEach(link => {
        const view = (link.getAttribute("href") || "").replace(/^#/, "");
        link.classList.toggle("d-none", !allowed.includes(view));
    });
}

function isViewAllowed(viewId) {
    const permissions = {
        ADMIN: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments','ai-dashboard','reports','users','settings','audit-logs'],
        MANAGER: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments','ai-dashboard','reports'],
        STAFF: ['dashboard','parking-slots','parking-entry','parking-exit','vehicles','reservations','payments'],
        SECURITY: ['dashboard','parking-slots','vehicles','ai-dashboard','audit-logs'],
        CUSTOMER: ['dashboard','parking-slots','vehicles','reservations','payments']
    };
    return (permissions[currentRole] || permissions.CUSTOMER).includes(viewId);
}

function showAccessDenied(viewId) {
    const pretty = viewId.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    const errorBox = document.getElementById("access-denied-message");
    if (errorBox) {
        errorBox.textContent = `${pretty} is not available for your ${currentRole.toLowerCase()} account.`;
        errorBox.classList.remove("d-none");
        setTimeout(() => errorBox.classList.add("d-none"), 3500);
    }
    switchNav('dashboard');
}

// -------------------------------------------------------------------
// API Data Loaders & Charts (Black Theme Colors)
// -------------------------------------------------------------------
async function loadDashboardData() {
    try {
        const res = await fetch(`${API_BASE}/reports/daily`);
        const result = await res.json();
        if (result.success) {
            const d = result.data;
            document.getElementById("dash-total-slots").textContent = d.totalSlots;
            document.getElementById("dash-available-slots").textContent = d.availableSlots;
            document.getElementById("dash-occupied-slots").textContent = d.occupiedSlots;
            document.getElementById("dash-revenue").textContent = `₹${d.todayRevenue.toLocaleString()}`;

            renderOccupancyChart(d.availableSlots, d.occupiedSlots, d.reservedSlots, d.maintenanceSlots);
        }

        const activeRes = await fetch(`${API_BASE}/parking/active`);
        const activeData = await activeRes.json();
        if (activeData.success) {
            currentActiveSessions = activeData.data;
            renderActiveTable(currentActiveSessions);
        }

        renderTrafficChart();

    } catch (err) {
        console.error("Error loading dashboard data:", err);
    }
}

function renderActiveTable(sessions) {
    const tbody = document.querySelector("#dash-active-table tbody");
    tbody.innerHTML = "";

    if (sessions.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" class="text-center text-secondary py-3">No vehicles currently parked</td></tr>`;
        return;
    }

    sessions.forEach(s => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td class="fw-bold text-gold font-monospace">${s.ticket_number}</td>
            <td><span class="badge bg-black border border-dark-secondary text-cyan font-monospace fs-6">${s.vehicle_number}</span></td>
            <td><span class="badge bg-black border border-dark-secondary text-light">${s.vehicle_type || 'CAR'}</span></td>
            <td><span class="fw-bold text-emerald">${s.slot_number}</span></td>
            <td class="small text-secondary">${new Date(s.entry_time).toLocaleTimeString()}</td>
            <td><span class="badge bg-crimson text-white">PARKED</span></td>
            <td>
                <button class="btn btn-sm btn-outline-crimson" onclick="quickExit('${s.ticket_number}')"><i class="fa-solid fa-right-from-bracket me-1"></i>Exit</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function renderTrafficChart() {
    const ctx = document.getElementById("trafficChart").getContext("2d");
    if (trafficChartInstance) trafficChartInstance.destroy();

    trafficChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: ['06:00', '08:00', '10:00', '12:00', '14:00', '16:00', '18:00', '20:00'],
            datasets: [{
                label: 'Vehicle Entries',
                data: [12, 45, 88, 72, 65, 94, 110, 52],
                borderColor: '#00f2fe',
                backgroundColor: 'rgba(0, 242, 254, 0.12)',
                fill: true,
                tension: 0.4
            }, {
                label: 'Vehicle Exits',
                data: [5, 20, 50, 68, 70, 85, 105, 78],
                borderColor: '#ff3366',
                backgroundColor: 'rgba(255, 51, 102, 0.12)',
                fill: true,
                tension: 0.4
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { labels: { color: '#adb5bd' } } },
            scales: {
                x: { ticks: { color: '#888' }, grid: { color: '#1e2333' } },
                y: { ticks: { color: '#888' }, grid: { color: '#1e2333' } }
            }
        }
    });
}

function renderOccupancyChart(avail, occ, res, maint) {
    const ctx = document.getElementById("occupancyChart").getContext("2d");
    if (occupancyChartInstance) occupancyChartInstance.destroy();

    occupancyChartInstance = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['Available', 'Occupied', 'Reserved', 'Maintenance'],
            datasets: [{
                data: [avail, occ, res, maint],
                backgroundColor: ['#00e676', '#ff3366', '#ffd700', '#6c757d'],
                borderWidth: 0
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { position: 'bottom', labels: { color: '#adb5bd' } } }
        }
    });
}

// -------------------------------------------------------------------
// Parking Slots Visualizer
// -------------------------------------------------------------------
async function fetchSlotsAndRender(areaFilter) {
    try {
        const res = await fetch(`${API_BASE}/slots`);
        const result = await res.json();
        if (result.success) {
            currentSlotsData = result.data;
            renderSlotLayout(areaFilter);
        }
    } catch (err) {
        console.error("Error loading slots:", err);
    }
}

function renderSlotLayout(areaId) {
    const container = document.getElementById("slot-grid-container");
    container.innerHTML = "";

    let filtered = currentSlotsData;
    if (areaId !== 'ALL') {
        filtered = currentSlotsData.filter(s => s.area_id == areaId);
    }

    filtered.forEach(s => {
        const col = document.createElement("div");
        col.className = "col-xl-2 col-lg-3 col-md-4 col-6";

        const card = document.createElement("div");
        card.className = `slot-card status-${s.status}`;
        card.onclick = () => showSlotDetails(s);

        let icon = "fa-car";
        if (s.vehicle_type === 'BIKE' || s.vehicle_type === 'SCOOTER') icon = "fa-motorcycle";
        else if (s.vehicle_type === 'SUV') icon = "fa-truck-monster";

        card.innerHTML = `
            <div class="d-flex justify-content-between align-items-center mb-2">
                <i class="fa-solid ${icon} fs-5"></i>
                <span class="badge bg-black border border-dark-secondary">${s.vehicle_type || 'CAR'}</span>
            </div>
            <div class="slot-number my-2 fw-bold fs-4">${s.slot_number}</div>
            <small class="fw-bold uppercase">${s.status}</small>
        `;
        col.appendChild(card);
        container.appendChild(col);
    });
}

function showSlotDetails(slot) {
    alert(`Slot: ${slot.slot_number}\nStatus: ${slot.status}\nType: ${slot.vehicle_type}\nArea ID: ${slot.area_id}`);
}

// -------------------------------------------------------------------
// Entry & Exit Handlers
// -------------------------------------------------------------------
async function prepareEntryForm() {
    try {
        const res = await fetch(`${API_BASE}/slots/available`);
        const result = await res.json();
        const select = document.getElementById("entry-slot-select");
        select.innerHTML = "";

        if (result.success && result.data.length > 0) {
            result.data.forEach(s => {
                select.innerHTML += `<option value="${s.slot_number}">${s.slot_number} (${s.vehicle_type})</option>`;
            });
        } else {
            select.innerHTML = `<option value="">No Available Slots</option>`;
        }
    } catch (err) {
        console.error(err);
    }
}

async function getAISlotRecommendation() {
    try {
        const res = await fetch(`${API_BASE}/ai/recommend-slot`);
        const result = await res.json();
        if (result.success) {
            document.getElementById("entry-slot-select").value = result.recommended_slot;
            alert(`🤖 AI Recommends Slot: ${result.recommended_slot}\nReason: ${result.reason}`);
        }
    } catch (err) {
        console.error(err);
    }
}

async function handleVehicleEntry(e) {
    e.preventDefault();

    const payload = {
        vehicle_number: document.getElementById("entry-vehicle-number").value,
        vehicle_type: document.getElementById("entry-vehicle-type").value,
        owner_name: document.getElementById("entry-owner-name").value,
        owner_phone: document.getElementById("entry-owner-phone").value,
        slot_number: document.getElementById("entry-slot-select").value
    };

    try {
        const res = await fetch(`${API_BASE}/parking/entry`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        const result = await res.json();

        if (result.success) {
            showDigitalTicket(result.data);
            document.getElementById("form-vehicle-entry").reset();
            loadDashboardData();
        } else {
            alert(`Error: ${result.message}`);
        }
    } catch (err) {
        alert("Failed to submit entry request.");
    }
}

function showDigitalTicket(ticket) {
    const modalContent = document.getElementById("ticket-modal-content");
    modalContent.innerHTML = `
        <div class="border border-dark-secondary rounded p-4 text-start font-monospace bg-black-surface">
            <div class="text-center fw-bold fs-4 text-uppercase text-gold mb-2">SmartPark AI Ticket</div>
            <div class="text-center small text-secondary border-bottom border-dark-secondary pb-3 mb-3">Keep ticket for exit verification</div>
            
            <div class="d-flex justify-content-between mb-2"><span>TICKET NO:</span><span class="fw-bold text-gold">${ticket.ticket_number}</span></div>
            <div class="d-flex justify-content-between mb-2"><span>VEHICLE:</span><span class="fw-bold text-cyan">${ticket.vehicle_number} (${ticket.vehicle_type})</span></div>
            <div class="d-flex justify-content-between mb-2"><span>SLOT ASSIGNED:</span><span class="fw-bold text-emerald">${ticket.slot_number}</span></div>
            <div class="d-flex justify-content-between mb-3"><span>ENTRY TIME:</span><span class="fw-bold text-light">${new Date(ticket.entry_time).toLocaleString()}</span></div>
            
            <div class="text-center pt-3 border-top border-dark-secondary">
                <i class="fa-solid fa-qrcode display-1 text-light"></i>
                <div class="small text-secondary mt-1">Scan at exit gate</div>
            </div>
        </div>
    `;

    const modal = new bootstrap.Modal(document.getElementById("ticketModal"));
    modal.show();
}

function quickExit(ticketNo) {
    switchNav('parking-exit');
    document.getElementById("exit-lookup-input").value = ticketNo;
    lookupExitSession();
}

async function lookupExitSession() {
    const input = document.getElementById("exit-lookup-input").value.trim();
    if (!input) return;

    try {
        const res = await fetch(`${API_BASE}/parking/calculate-fee?ticket=${encodeURIComponent(input)}`);
        const result = await res.json();

        if (result.success) {
            const data = result.data;
            document.getElementById("exit-details-container").classList.remove("d-none");
            document.getElementById("exit-ticket-no").textContent = data.ticket_number;
            document.getElementById("exit-vehicle-slot").textContent = `${data.vehicle_number} | Slot ${data.slot_number}`;
            document.getElementById("exit-entry-time").textContent = new Date(data.entry_time).toLocaleTimeString();
            document.getElementById("exit-current-time").textContent = new Date().toLocaleTimeString();
            document.getElementById("exit-duration").textContent = data.duration_formatted;
            document.getElementById("exit-total-amount").textContent = `₹${data.total_amount.toFixed(2)}`;
        } else {
            alert(result.message);
            document.getElementById("exit-details-container").classList.add("d-none");
        }
    } catch (err) {
        console.error(err);
    }
}

async function processExitPayment() {
    const ticketNo = document.getElementById("exit-ticket-no").textContent;
    const method = document.getElementById("exit-payment-method").value;

    try {
        const res = await fetch(`${API_BASE}/parking/exit`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ ticket_number: ticketNo, payment_method: method })
        });
        const result = await res.json();

        if (result.success) {
            showDigitalReceipt(result.receipt, result.session);
            document.getElementById("exit-details-container").classList.add("d-none");
            document.getElementById("exit-lookup-input").value = "";
            loadDashboardData();
        } else {
            alert(result.message);
        }
    } catch (err) {
        alert("Exit payment processing failed.");
    }
}

function showDigitalReceipt(receipt, session) {
    const content = document.getElementById("receipt-modal-content");
    content.innerHTML = `
        <div class="border border-emerald rounded p-4 text-start font-monospace bg-black-surface">
            <div class="text-center fw-bold fs-4 text-emerald mb-1">PAYMENT RECEIPT</div>
            <div class="text-center small text-secondary border-bottom border-dark-secondary pb-3 mb-3">Transaction #${receipt.transaction_number}</div>

            <div class="d-flex justify-content-between mb-2"><span>Vehicle Number:</span><span class="fw-bold text-light">${session.vehicle_number}</span></div>
            <div class="d-flex justify-content-between mb-2"><span>Slot Occupied:</span><span class="fw-bold text-light">${session.slot_number}</span></div>
            <div class="d-flex justify-content-between mb-2"><span>Duration:</span><span class="fw-bold text-cyan">${session.duration_minutes} Minutes</span></div>
            <div class="d-flex justify-content-between mb-2"><span>Base Rate:</span><span class="fw-bold text-light">₹${session.parking_rate.toFixed(2)}</span></div>
            <div class="d-flex justify-content-between mb-2"><span>Tax (18%):</span><span class="fw-bold text-light">₹${session.tax.toFixed(2)}</span></div>
            <hr class="border-dark-secondary">
            <div class="d-flex justify-content-between mb-2 fs-5"><span class="fw-bold text-emerald">TOTAL PAID:</span><span class="fw-bold text-emerald">₹${receipt.amount.toFixed(2)}</span></div>
            <div class="d-flex justify-content-between text-secondary small"><span>Payment Method:</span><span>${receipt.payment_method}</span></div>
        </div>
    `;
    const modal = new bootstrap.Modal(document.getElementById("receiptModal"));
    modal.show();
}

// -------------------------------------------------------------------
// AI Dashboard & Data Loaders
// -------------------------------------------------------------------
async function loadAIDashboard() {
    try {
        const res = await fetch(`${API_BASE}/ai/demand-prediction`);
        const result = await res.json();
        if (result.success) {
            renderAIDemandChart(result.data);
        }

        const alertRes = await fetch(`${API_BASE}/ai/alerts`);
        const alertData = await alertRes.json();
        if (alertData.success) {
            renderAIAlerts(alertData.data);
        }
    } catch (err) {
        console.error(err);
    }
}

function renderAIDemandChart(data) {
    const ctx = document.getElementById("aiDemandChart").getContext("2d");
    if (aiDemandChartInstance) aiDemandChartInstance.destroy();

    aiDemandChartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.map(d => d.time),
            datasets: [{
                label: 'Predicted Occupancy %',
                data: data.map(d => d.occupancy_percent),
                borderColor: '#ffd700',
                backgroundColor: 'rgba(255, 215, 0, 0.15)',
                fill: true,
                tension: 0.3
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { labels: { color: '#adb5bd' } } },
            scales: {
                x: { ticks: { color: '#888' }, grid: { color: '#1e2333' } },
                y: { ticks: { color: '#888' }, grid: { color: '#1e2333' }, min: 0, max: 100 }
            }
        }
    });
}

function renderAIAlerts(alerts) {
    const list = document.getElementById("ai-alerts-list");
    list.innerHTML = "";
    alerts.forEach(a => {
        const badgeColor = a.level === 'HIGH' ? 'bg-crimson text-white' : (a.level === 'MEDIUM' ? 'bg-gold text-black' : 'bg-cyan text-black');
        list.innerHTML += `
            <li class="list-group-item bg-transparent text-light border-dark-secondary d-flex justify-content-between align-items-center py-3">
                <div>
                    <span class="badge ${badgeColor} me-2">${a.level}</span>
                    <strong class="font-monospace text-gold">${a.vehicle_number}</strong> - ${a.reason}
                </div>
                <small class="text-secondary">${a.timestamp}</small>
            </li>
        `;
    });
}

async function loadVehicles() {
    const res = await fetch(`${API_BASE}/vehicles`);
    const data = await res.json();
    if (data.success) {
        const tbody = document.querySelector("#vehicles-table tbody");
        tbody.innerHTML = "";
        data.data.forEach(v => {
            tbody.innerHTML += `
                <tr>
                    <td>${v.id}</td>
                    <td><span class="badge bg-black border border-dark-secondary text-cyan font-monospace fs-6">${v.vehicle_number}</span></td>
                    <td>${v.vehicle_type}</td>
                    <td>${v.brand || 'N/A'} ${v.model || ''}</td>
                    <td>${v.owner_name}</td>
                    <td>${v.owner_phone}</td>
                    <td><span class="badge bg-emerald text-black">${v.vehicle_status}</span></td>
                </tr>
            `;
        });
    }
}

async function loadReservations() {
    const res = await fetch(`${API_BASE}/reservations`);
    const data = await res.json();
    if (data.success) {
        const tbody = document.querySelector("#reservations-table tbody");
        tbody.innerHTML = "";
        data.data.forEach(r => {
            tbody.innerHTML += `
                <tr>
                    <td>#RES-${r.id}</td>
                    <td>${r.user_name}</td>
                    <td><span class="badge bg-black border border-dark-secondary text-gold">${r.vehicle_number}</span></td>
                    <td>${r.area_name} / <strong class="text-cyan">${r.slot_number}</strong></td>
                    <td>${r.reservation_date}</td>
                    <td>${r.start_time} - ${r.end_time}</td>
                    <td><span class="badge bg-emerald text-black">${r.status}</span></td>
                </tr>
            `;
        });
    }
}

async function loadPayments() {
    const res = await fetch(`${API_BASE}/payments`);
    const data = await res.json();
    if (data.success) {
        const tbody = document.querySelector("#payments-table tbody");
        tbody.innerHTML = "";
        data.data.forEach(p => {
            tbody.innerHTML += `
                <tr>
                    <td class="fw-bold text-emerald">${p.transaction_number}</td>
                    <td>${p.ticket_number || 'N/A'}</td>
                    <td class="fw-bold">₹${p.amount.toFixed(2)}</td>
                    <td><span class="badge bg-black border border-dark-secondary text-cyan">${p.payment_method}</span></td>
                    <td class="small text-secondary">${new Date(p.payment_time).toLocaleString()}</td>
                    <td>${p.received_by}</td>
                    <td><span class="badge bg-emerald text-black">${p.payment_status}</span></td>
                </tr>
            `;
        });
    }
}

async function loadUsers() {
    const res = await fetch(`${API_BASE}/users`);
    const data = await res.json();
    if (data.success) {
        const tbody = document.querySelector("#users-table tbody");
        tbody.innerHTML = "";
        data.data.forEach(u => {
            tbody.innerHTML += `
                <tr>
                    <td>${u.id}</td>
                    <td class="fw-bold">${u.full_name}</td>
                    <td>${u.email}</td>
                    <td>${u.phone}</td>
                    <td><span class="badge bg-cyan text-black">${u.role}</span></td>
                    <td><span class="badge bg-emerald text-black">${u.status}</span></td>
                </tr>
            `;
        });
    }
}

async function loadAuditLogs() {
    const res = await fetch(`${API_BASE}/audit-logs`);
    const data = await res.json();
    if (data.success) {
        const tbody = document.querySelector("#audit-table tbody");
        tbody.innerHTML = "";
        data.data.forEach(l => {
            tbody.innerHTML += `
                <tr>
                    <td>#LOG-${l.id}</td>
                    <td><span class="badge bg-black border border-dark-secondary text-cyan">${l.action}</span></td>
                    <td>${l.entity_type} (#${l.entity_id})</td>
                    <td class="small text-light">${l.description}</td>
                    <td class="small text-secondary">${new Date(l.created_at).toLocaleString()}</td>
                </tr>
            `;
        });
    }
}

function openANPRModal() {
    const modal = new bootstrap.Modal(document.getElementById("anprModal"));
    modal.show();
}

async function simulateANPRScan() {
    try {
        const res = await fetch(`${API_BASE}/ai/plate-recognition`, { method: 'POST' });
        const result = await res.json();

        if (result.success) {
            document.getElementById("anpr-scan-result").classList.remove("d-none");
            document.getElementById("anpr-detected-num").textContent = result.detected_plate;
            document.getElementById("entry-vehicle-number").value = result.detected_plate;
        }
    } catch (err) {
        console.error(err);
    }
}

function exportReportCSV() {
    alert("Exporting Financial Report CSV...");
}
