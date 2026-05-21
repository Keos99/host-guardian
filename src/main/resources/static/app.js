const DASHBOARD_REFRESH_INTERVAL_MS = 5000;

const state = {
    hosts: [],
    groups: [],
    dashboard: null,
    activeTab: "dashboard",
    dashboardRefreshInFlight: null,
    dashboardRefreshTimerId: null,
    openActionMenuId: null,
    dashboardRequestSeq: 0
};

document.addEventListener("DOMContentLoaded", () => {
    bindEvents();
    initializeTabs();
    refreshAll().catch(handleError);
    startDashboardPolling();
});

function bindEvents() {
    document.addEventListener("click", (event) => {
        if (!event.target.closest?.(".action-menu-shell, .floating-action-menu")) {
            closeActionMenus();
        }
    });

    document.getElementById("refreshDashboardButton").addEventListener("click", (event) => {
        runWithButtonBusy(event.currentTarget, () => refreshDashboard()).catch(handleError);
    });
    document.getElementById("clearGroupFilterButton").addEventListener("click", () => {
        clearMultiSelect(document.getElementById("groupFilter"));
        refreshDashboard().catch(handleError);
    });
    document.getElementById("groupFilter").addEventListener("change", () => refreshDashboard().catch(handleError));

    document.getElementById("serviceForm").addEventListener("submit", (event) => submitServiceForm(event).catch(handleError));
    document.getElementById("hostForm").addEventListener("submit", (event) => submitHostForm(event).catch(handleError));
    document.getElementById("groupForm").addEventListener("submit", (event) => submitGroupForm(event).catch(handleError));

    document.getElementById("resetServiceFormButton").addEventListener("click", resetServiceForm);
    document.getElementById("resetHostFormButton").addEventListener("click", resetHostForm);
    document.getElementById("resetGroupFormButton").addEventListener("click", resetGroupForm);

    document.getElementById("hostConnectionMode").addEventListener("change", updateHostConnectionModeFields);
    document.getElementById("serviceManualRestartEnabled").addEventListener("change", updateRestartCommandMode);
    document.getElementById("tabSwitchButton").addEventListener("click", toggleActiveTab);

    document.getElementById("servicesTableBody").addEventListener("click", (event) => onServicesTableClick(event).catch(handleError));
    document.getElementById("hostsTableBody").addEventListener("click", (event) => onHostsTableClick(event).catch(handleError));
    document.getElementById("groupsTableBody").addEventListener("click", (event) => onGroupsTableClick(event).catch(handleError));
    document.addEventListener("click", (event) => onFloatingActionMenuClick(event).catch(handleError));
    window.addEventListener("resize", restoreOpenActionMenuPosition);
}

async function refreshAll() {
    await Promise.all([loadHosts(), loadGroups()]);
    await refreshDashboard();
    updateHostConnectionModeFields();
    updateRestartCommandMode();
}

function startDashboardPolling() {
    if (state.dashboardRefreshTimerId) {
        window.clearInterval(state.dashboardRefreshTimerId);
    }

    state.dashboardRefreshTimerId = window.setInterval(() => {
        refreshDashboard({silent: true, skipIfBusy: true});
    }, DASHBOARD_REFRESH_INTERVAL_MS);
}

async function refreshDashboard(options = {}) {
    const {silent = false, skipIfBusy = false} = options;
    if (skipIfBusy && state.dashboardRefreshInFlight) {
        return state.dashboardRefreshInFlight;
    }

    const refresh = loadDashboard().catch((error) => {
        if (silent) {
            console.error(error);
        } else {
            handleError(error);
        }
    });

    state.dashboardRefreshInFlight = refresh;
    refresh.finally(() => {
        if (state.dashboardRefreshInFlight === refresh) {
            state.dashboardRefreshInFlight = null;
        }
    });

    return refresh;
}

async function loadHosts() {
    state.hosts = await requestJson("/api/hosts");
    renderHostsTable();
    populateHostSelect();
    updateTabViewportHeight();
}

async function loadGroups() {
    state.groups = await requestJson("/api/groups");
    renderGroupsTable();
    populateGroupFilter();
    populateServiceGroupSelect();
    updateTabViewportHeight();
}

async function loadDashboard() {
    const params = new URLSearchParams();
    getSelectedValues(document.getElementById("groupFilter")).forEach((value) => params.append("groupId", value));
    const url = params.toString() ? `/api/dashboard?${params}` : "/api/dashboard";
    const requestSeq = ++state.dashboardRequestSeq;
    const dashboard = await requestJson(url);
    if (requestSeq !== state.dashboardRequestSeq) {
        return;
    }

    state.dashboard = dashboard;
    renderSummary(state.dashboard.summary);
    renderServicesTable(state.dashboard.services);
    updateTabViewportHeight();
}

function initializeTabs() {
    setActiveTab(state.activeTab);
    window.addEventListener("resize", updateTabViewportHeight);
}

function toggleActiveTab() {
    setActiveTab(state.activeTab === "dashboard" ? "configuration" : "dashboard");
}

function setActiveTab(tabName) {
    state.activeTab = tabName;
    const isConfiguration = tabName === "configuration";
    const track = document.getElementById("tabsTrack");
    const dashboardTab = document.getElementById("dashboardTab");
    const configurationTab = document.getElementById("configurationTab");
    const switchButton = document.getElementById("tabSwitchButton");
    const switchIcon = document.getElementById("tabSwitchIcon");
    const switchText = document.getElementById("tabSwitchText");
    const label = isConfiguration ? "Открыть dashboard" : "Открыть настройки";

    track.classList.toggle("is-configuration", isConfiguration);
    dashboardTab.classList.toggle("is-active", !isConfiguration);
    configurationTab.classList.toggle("is-active", isConfiguration);
    dashboardTab.setAttribute("aria-hidden", String(isConfiguration));
    configurationTab.setAttribute("aria-hidden", String(!isConfiguration));
    switchButton.classList.toggle("is-back", isConfiguration);
    switchButton.setAttribute("aria-label", label);
    switchButton.setAttribute("title", label);
    switchIcon.textContent = isConfiguration ? "<" : ">";
    switchText.textContent = label;

    updateTabViewportHeight();
}

function updateTabViewportHeight() {
    const viewport = document.getElementById("tabsViewport");
    const activeTab = document.getElementById(state.activeTab === "configuration" ? "configurationTab" : "dashboardTab");
    if (!viewport || !activeTab) {
        return;
    }

    viewport.style.height = `${activeTab.scrollHeight}px`;
}

function populateHostSelect() {
    const select = document.getElementById("serviceHostId");
    const currentValue = select.value;
    select.innerHTML = state.hosts
        .map((host) => `<option value="${host.id}">${escapeHtml(host.name)} (${escapeHtml(host.connectionMode)})</option>`)
        .join("");

    if (currentValue) {
        select.value = currentValue;
    }
}

function populateGroupFilter() {
    const select = document.getElementById("groupFilter");
    const selected = new Set(getSelectedValues(select));

    select.innerHTML = state.groups
        .map((group) => `<option value="${group.id}">${escapeHtml(group.name)}</option>`)
        .join("");

    Array.from(select.options).forEach((option) => {
        option.selected = selected.has(option.value);
    });
}

function populateServiceGroupSelect() {
    const select = document.getElementById("serviceGroupId");
    const currentValue = select.value;
    select.innerHTML = [
        `<option value="">Без группы</option>`,
        ...state.groups.map((group) => `<option value="${group.id}">${escapeHtml(group.name)}</option>`)
    ].join("");

    if (currentValue) {
        select.value = currentValue;
    }
}

function renderSummary(summary) {
    const items = [
        ["Всего", summary.total],
        ["UP", summary.up],
        ["DOWN", summary.down],
        ["PAUSED", summary.paused],
        ["RESTARTING", summary.restarting],
        ["ERROR", summary.error],
        ["UNKNOWN", summary.unknown]
    ];

    document.getElementById("summaryGrid").innerHTML = items
        .map(([label, value]) => `
            <article class="summary-card">
                <span>${escapeHtml(String(label))}</span>
                <strong>${escapeHtml(String(value))}</strong>
            </article>
        `)
        .join("");
}

function renderServicesTable(services) {
    const tbody = document.getElementById("servicesTableBody");
    if (!services.length) {
        tbody.innerHTML = `<tr><td colspan="10" class="empty-state">Сервисы пока не добавлены.</td></tr>`;
        return;
    }

    tbody.innerHTML = services.map((service) => `
        <tr>
            <td>
                <div class="service-cell">
                    <strong>${escapeHtml(service.name)}</strong>
                    <small>${escapeHtml(service.monitoringEnabled ? "monitoring enabled" : "monitoring paused")}</small>
                </div>
            </td>
            <td>${escapeHtml(service.hostName)}<br><small>${escapeHtml(service.hostAddress)}</small></td>
            <td>${escapeHtml(service.groupName ?? "—")}</td>
            <td><span class="status-badge status-${service.status.toLowerCase()}">${escapeHtml(service.status)}</span></td>
            <td>${renderBooleanPill(service.processRunning)}</td>
            <td>${escapeHtml(service.lastKnownPid ?? "—")}</td>
            <td>${renderHealthCheckPill(service)}</td>
            <td>${escapeHtml(formatDateTime(service.lastCheckAt))}</td>
            <td class="message-cell">${escapeHtml(service.lastMessage ?? "—")}</td>
            <td>
                ${renderActionMenu(`service-${service.id}`, [
                    {
                        action: "toggle-monitoring",
                        id: service.id,
                        label: service.monitoringEnabled ? "Пауза" : "Включить",
                        attributes: `data-enabled="${service.monitoringEnabled}"`
                    },
                    {action: "restart", id: service.id, label: "Restart"},
                    {action: "check", id: service.id, label: "Check"},
                    {action: "edit-service", id: service.id, label: "Edit"},
                    {action: "delete-service", id: service.id, label: "Delete", danger: true}
                ])}
            </td>
        </tr>
    `).join("");
    restoreOpenActionMenuPosition();
}

function renderHostsTable() {
    const tbody = document.getElementById("hostsTableBody");
    tbody.innerHTML = state.hosts.map((host) => `
        <tr>
            <td>${escapeHtml(host.name)}</td>
            <td>${escapeHtml(host.connectionMode)}</td>
            <td>${escapeHtml(host.address)}</td>
            <td>
                ${renderActionMenu(`host-${host.id}`, [
                    {action: "edit-host", id: host.id, label: "Edit"},
                    {action: "delete-host", id: host.id, label: "Delete", danger: true}
                ])}
            </td>
        </tr>
    `).join("");
    restoreOpenActionMenuPosition();
}

function renderGroupsTable() {
    const tbody = document.getElementById("groupsTableBody");
    tbody.innerHTML = state.groups.map((group) => `
        <tr>
            <td>${escapeHtml(group.name)}</td>
            <td>${escapeHtml(group.description ?? "—")}</td>
            <td>
                ${renderActionMenu(`group-${group.id}`, [
                    {action: "edit-group", id: group.id, label: "Edit"},
                    {action: "delete-group", id: group.id, label: "Delete", danger: true}
                ])}
            </td>
        </tr>
    `).join("");
    restoreOpenActionMenuPosition();
}

function renderActionMenu(menuId, items) {
    const menuOpen = state.openActionMenuId === menuId;

    return `
        <div class="action-menu-shell">
            <button class="hamburger-button" type="button" data-menu-toggle data-menu-id="${escapeHtml(menuId)}"
                    aria-haspopup="menu" aria-expanded="${String(menuOpen)}"
                    aria-controls="action-menu-${escapeHtml(menuId)}" aria-label="Открыть действия">
                <span></span>
                <span></span>
                <span></span>
            </button>
            <div id="action-menu-${escapeHtml(menuId)}" class="action-menu action-menu-template" role="menu" hidden>
                ${items.map((item) => `
                    <button class="action-menu-item${item.danger ? " danger" : ""}" type="button" role="menuitem"
                            data-action="${escapeHtml(item.action)}" data-id="${escapeHtml(item.id)}" ${item.attributes ?? ""}>
                        ${escapeHtml(item.label)}
                    </button>
                `).join("")}
            </div>
        </div>
    `;
}

function handleActionMenuClick(event) {
    const toggle = event.target.closest?.("[data-menu-toggle]");
    if (!toggle) {
        return false;
    }

    const shell = toggle.closest(".action-menu-shell");
    const menu = shell?.querySelector(".action-menu");
    if (!menu) {
        return false;
    }

    const menuId = toggle.dataset.menuId;
    const shouldOpen = state.openActionMenuId !== menuId;
    closeActionMenus(shell);
    state.openActionMenuId = shouldOpen ? menuId : null;
    toggle.setAttribute("aria-expanded", String(shouldOpen));
    if (shouldOpen) {
        showFloatingActionMenu(menu, toggle);
    }
    return true;
}

function closeActionMenus(exceptShell = null) {
    if (!exceptShell) {
        state.openActionMenuId = null;
    }

    hideFloatingActionMenu();
    document.querySelectorAll(".action-menu-shell").forEach((shell) => {
        if (shell === exceptShell) {
            return;
        }

        shell.closest(".table-wrap")?.classList.remove("has-open-action-menu");
        const toggle = shell.querySelector("[data-menu-toggle]");
        const menu = shell.querySelector(".action-menu");
        if (menu) {
            menu.hidden = true;
            menu.style.left = "";
            menu.style.top = "";
        }
        if (toggle) {
            toggle.setAttribute("aria-expanded", "false");
        }
    });
}

function restoreOpenActionMenuPosition() {
    if (!state.openActionMenuId) {
        hideFloatingActionMenu();
        return;
    }

    const toggle = Array.from(document.querySelectorAll("[data-menu-toggle]"))
        .find((button) => button.dataset.menuId === state.openActionMenuId);
    const menu = toggle?.closest(".action-menu-shell")?.querySelector(".action-menu");
    if (menu && toggle) {
        toggle.setAttribute("aria-expanded", "true");
        showFloatingActionMenu(menu, toggle);
    } else {
        state.openActionMenuId = null;
        hideFloatingActionMenu();
    }
}

function showFloatingActionMenu(sourceMenu, toggle) {
    const floatingMenu = getFloatingActionMenu();
    floatingMenu.innerHTML = sourceMenu.innerHTML;
    floatingMenu.hidden = false;
    positionActionMenu(floatingMenu, toggle);
}

function hideFloatingActionMenu() {
    const floatingMenu = document.getElementById("floatingActionMenu");
    if (floatingMenu) {
        floatingMenu.hidden = true;
        floatingMenu.innerHTML = "";
        floatingMenu.style.left = "";
        floatingMenu.style.top = "";
    }
    document.querySelectorAll(".table-wrap.has-open-action-menu")
        .forEach((element) => element.classList.remove("has-open-action-menu"));
}

function getFloatingActionMenu() {
    let floatingMenu = document.getElementById("floatingActionMenu");
    if (!floatingMenu) {
        floatingMenu = document.createElement("div");
        floatingMenu.id = "floatingActionMenu";
        floatingMenu.className = "action-menu floating-action-menu";
        floatingMenu.setAttribute("role", "menu");
        floatingMenu.hidden = true;
        document.body.appendChild(floatingMenu);
    }

    return floatingMenu;
}

function positionActionMenu(menu, toggle) {
    const margin = 12;
    const gap = 8;
    toggle.closest(".table-wrap")?.classList.add("has-open-action-menu");
    const toggleRect = toggle.getBoundingClientRect();
    const menuRect = menu.getBoundingClientRect();
    const viewportWidth = window.innerWidth || document.documentElement.clientWidth;
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
    const left = Math.min(
            viewportWidth - menuRect.width - margin,
            Math.max(margin, toggleRect.right - menuRect.width)
    );
    const bottomTop = toggleRect.bottom + gap;
    const top = bottomTop + menuRect.height <= viewportHeight - margin
            ? bottomTop
            : Math.max(margin, toggleRect.top - menuRect.height - gap);

    menu.style.left = `${left}px`;
    menu.style.top = `${top}px`;
}

async function submitServiceForm(event) {
    event.preventDefault();

    await runWithButtonBusy(getSubmitButton(event), async () => {
        const id = document.getElementById("serviceId").value;
        const payload = {
            name: document.getElementById("serviceName").value,
            hostId: Number(document.getElementById("serviceHostId").value),
            groupId: document.getElementById("serviceGroupId").value ? Number(document.getElementById("serviceGroupId").value) : null,
            processMatch: document.getElementById("serviceProcessMatch").value,
            executionPath: document.getElementById("serviceExecutionPath").value || null,
            startCommand: document.getElementById("serviceStartCommand").value,
            manualRestartEnabled: document.getElementById("serviceManualRestartEnabled").checked,
            restartCommand: document.getElementById("serviceRestartCommand").value,
            healthUrl: document.getElementById("serviceHealthUrl").value || null,
            healthTimeoutSeconds: Number(document.getElementById("serviceHealthTimeoutSeconds").value),
            restartCooldownSeconds: Number(document.getElementById("serviceRestartCooldownSeconds").value),
            restartWindowSeconds: Number(document.getElementById("serviceRestartWindowSeconds").value),
            maxRestartsInWindow: Number(document.getElementById("serviceMaxRestartsInWindow").value),
            monitoringEnabled: document.getElementById("serviceMonitoringEnabled").checked,
            description: document.getElementById("serviceDescription").value || null
        };

        const method = id ? "PUT" : "POST";
        const url = id ? `/api/services/${id}` : "/api/services";

        await requestJson(url, {
            method,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        showToast("Конфигурация сервиса сохранена.");
        resetServiceForm();
        await refreshDashboard();
    });
}

async function submitHostForm(event) {
    event.preventDefault();

    await runWithButtonBusy(getSubmitButton(event), async () => {
        const id = document.getElementById("hostId").value;
        const payload = {
            name: document.getElementById("hostName").value,
            connectionMode: document.getElementById("hostConnectionMode").value,
            address: document.getElementById("hostAddress").value,
            sshPort: Number(document.getElementById("hostSshPort").value),
            sshUser: document.getElementById("hostSshUser").value || null,
            privateKeyPath: document.getElementById("hostPrivateKeyPath").value || null,
            description: document.getElementById("hostDescription").value || null
        };

        const method = id ? "PUT" : "POST";
        const url = id ? `/api/hosts/${id}` : "/api/hosts";

        await requestJson(url, {
            method,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        showToast("Хост сохранен.");
        resetHostForm();
        await refreshAll();
    });
}

async function submitGroupForm(event) {
    event.preventDefault();

    await runWithButtonBusy(getSubmitButton(event), async () => {
        const id = document.getElementById("groupId").value;
        const payload = {
            name: document.getElementById("groupName").value,
            description: document.getElementById("groupDescription").value || null
        };

        const method = id ? "PUT" : "POST";
        const url = id ? `/api/groups/${id}` : "/api/groups";

        await requestJson(url, {
            method,
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        showToast("Группа сохранена.");
        resetGroupForm();
        await refreshAll();
    });
}

async function onServicesTableClick(event) {
    if (handleActionMenuClick(event)) {
        return;
    }

    const button = event.target.closest?.("button[data-action]");
    if (!button) {
        return;
    }

    await handleServiceAction(button);
}

async function handleServiceAction(button) {
    const id = Number(button.dataset.id);
    const action = button.dataset.action;

    if (action === "edit-service") {
        closeActionMenus();
        editService(id);
        return;
    }

    if (action === "toggle-monitoring") {
        await runWithButtonBusy(button, async () => {
            const enabled = button.dataset.enabled === "true";
            await requestJson(`/api/services/${id}/monitoring?enabled=${String(!enabled)}`, {method: "PATCH"});
            showToast(enabled ? "Мониторинг приостановлен." : "Мониторинг включен.");
            await refreshDashboard();
            closeActionMenus();
        });
        return;
    }

    if (action === "restart") {
        await runWithButtonBusy(button, async () => {
            await requestVoid(`/api/services/${id}/restart`, {method: "POST"});
            showToast("Команда рестарта отправлена.");
            await refreshDashboard();
            closeActionMenus();
        });
        return;
    }

    if (action === "check") {
        await runWithButtonBusy(button, async () => {
            await requestVoid(`/api/services/${id}/check`, {method: "POST"});
            showToast("Проверка сервиса выполнена.");
            await refreshDashboard();
            closeActionMenus();
        });
        return;
    }

    if (action === "delete-service") {
        if (!window.confirm("Удалить сервис?")) {
            closeActionMenus();
            return;
        }
        await runWithButtonBusy(button, async () => {
            await requestVoid(`/api/services/${id}`, {method: "DELETE"});
            showToast("Сервис удален.");
            await refreshDashboard();
            closeActionMenus();
        });
    }
}

async function onHostsTableClick(event) {
    if (handleActionMenuClick(event)) {
        return;
    }

    const button = event.target.closest?.("button[data-action]");
    if (!button) {
        return;
    }

    await handleHostAction(button);
}

async function handleHostAction(button) {
    const id = Number(button.dataset.id);
    const action = button.dataset.action;

    if (action === "edit-host") {
        closeActionMenus();
        editHost(id);
        return;
    }

    if (action === "delete-host") {
        if (!window.confirm("Удалить хост?")) {
            closeActionMenus();
            return;
        }
        await runWithButtonBusy(button, async () => {
            await requestVoid(`/api/hosts/${id}`, {method: "DELETE"});
            showToast("Хост удален.");
            await refreshAll();
            closeActionMenus();
        });
    }
}

async function onGroupsTableClick(event) {
    if (handleActionMenuClick(event)) {
        return;
    }

    const button = event.target.closest?.("button[data-action]");
    if (!button) {
        return;
    }

    await handleGroupAction(button);
}

async function handleGroupAction(button) {
    const id = Number(button.dataset.id);
    const action = button.dataset.action;

    if (action === "edit-group") {
        closeActionMenus();
        editGroup(id);
        return;
    }

    if (action === "delete-group") {
        if (!window.confirm("Удалить группу?")) {
            closeActionMenus();
            return;
        }
        await runWithButtonBusy(button, async () => {
            await requestVoid(`/api/groups/${id}`, {method: "DELETE"});
            showToast("Группа удалена.");
            await refreshAll();
            closeActionMenus();
        });
    }
}

async function onFloatingActionMenuClick(event) {
    const button = event.target.closest?.(".floating-action-menu button[data-action]");
    if (!button) {
        return;
    }

    if (state.openActionMenuId?.startsWith("service-")) {
        await handleServiceAction(button);
        return;
    }
    if (state.openActionMenuId?.startsWith("host-")) {
        await handleHostAction(button);
        return;
    }
    if (state.openActionMenuId?.startsWith("group-")) {
        await handleGroupAction(button);
    }
}

function getSubmitButton(event) {
    return event.submitter ?? event.currentTarget?.querySelector('button[type="submit"]') ?? null;
}

async function runWithButtonBusy(button, action) {
    setButtonBusy(button, true);
    try {
        return await action();
    } finally {
        setButtonBusy(button, false);
    }
}

function setButtonBusy(button, busy) {
    if (!button) {
        return;
    }

    if (busy) {
        button.dataset.wasDisabled = String(button.disabled);
        button.disabled = true;
        button.classList.add("is-loading");
        button.setAttribute("aria-busy", "true");
        return;
    }

    button.disabled = button.dataset.wasDisabled === "true";
    delete button.dataset.wasDisabled;
    button.classList.remove("is-loading");
    button.removeAttribute("aria-busy");
}

function editService(id) {
    const service = state.dashboard.services.find((item) => item.id === id);
    if (!service) {
        return;
    }

    requestJson(`/api/services/${id}`)
        .then((fullService) => {
            document.getElementById("serviceId").value = fullService.id;
            document.getElementById("serviceName").value = fullService.name;
            document.getElementById("serviceHostId").value = fullService.hostId;
            document.getElementById("serviceGroupId").value = fullService.groupId ?? "";
            document.getElementById("serviceProcessMatch").value = fullService.processMatch;
            document.getElementById("serviceExecutionPath").value = fullService.executionPath ?? "";
            document.getElementById("serviceStartCommand").value = fullService.startCommand;
            document.getElementById("serviceManualRestartEnabled").checked = fullService.manualRestartEnabled;
            document.getElementById("serviceRestartCommand").value = fullService.restartCommand ?? "";
            document.getElementById("serviceHealthUrl").value = fullService.healthUrl ?? "";
            document.getElementById("serviceHealthTimeoutSeconds").value = fullService.healthTimeoutSeconds;
            document.getElementById("serviceRestartCooldownSeconds").value = fullService.restartCooldownSeconds;
            document.getElementById("serviceRestartWindowSeconds").value = fullService.restartWindowSeconds;
            document.getElementById("serviceMaxRestartsInWindow").value = fullService.maxRestartsInWindow;
            document.getElementById("serviceMonitoringEnabled").checked = fullService.monitoringEnabled;
            document.getElementById("serviceDescription").value = fullService.description ?? "";
            updateRestartCommandMode();
            setActiveTab("configuration");
            window.scrollTo({top: document.getElementById("serviceForm").offsetTop - 40, behavior: "smooth"});
        })
        .catch(handleError);
}

function editHost(id) {
    const host = state.hosts.find((item) => item.id === id);
    if (!host) {
        return;
    }

    document.getElementById("hostId").value = host.id;
    document.getElementById("hostName").value = host.name;
    document.getElementById("hostConnectionMode").value = host.connectionMode;
    document.getElementById("hostAddress").value = host.address;
    document.getElementById("hostSshPort").value = host.sshPort;
    document.getElementById("hostSshUser").value = host.sshUser ?? "";
    document.getElementById("hostPrivateKeyPath").value = host.privateKeyPath ?? "";
    document.getElementById("hostDescription").value = host.description ?? "";
    updateHostConnectionModeFields();
    setActiveTab("configuration");
    window.scrollTo({top: document.getElementById("hostForm").offsetTop - 40, behavior: "smooth"});
}

function editGroup(id) {
    const group = state.groups.find((item) => item.id === id);
    if (!group) {
        return;
    }

    document.getElementById("groupId").value = group.id;
    document.getElementById("groupName").value = group.name;
    document.getElementById("groupDescription").value = group.description ?? "";
    setActiveTab("configuration");
    window.scrollTo({top: document.getElementById("groupForm").offsetTop - 40, behavior: "smooth"});
}

function resetServiceForm() {
    document.getElementById("serviceForm").reset();
    document.getElementById("serviceId").value = "";
    document.getElementById("serviceMonitoringEnabled").checked = true;
    document.getElementById("serviceManualRestartEnabled").checked = false;
    document.getElementById("serviceHealthTimeoutSeconds").value = 3;
    document.getElementById("serviceRestartCooldownSeconds").value = 60;
    document.getElementById("serviceRestartWindowSeconds").value = 600;
    document.getElementById("serviceMaxRestartsInWindow").value = 3;
    updateRestartCommandMode();
}

function resetHostForm() {
    document.getElementById("hostForm").reset();
    document.getElementById("hostId").value = "";
    document.getElementById("hostAddress").value = "127.0.0.1";
    document.getElementById("hostSshPort").value = 22;
    document.getElementById("hostConnectionMode").value = "LOCAL";
    updateHostConnectionModeFields();
}

function resetGroupForm() {
    document.getElementById("groupForm").reset();
    document.getElementById("groupId").value = "";
}

function updateHostConnectionModeFields() {
    const mode = document.getElementById("hostConnectionMode").value;
    document.querySelectorAll(".ssh-only-field").forEach((field) => {
        field.classList.toggle("hidden", mode !== "SSH");
    });
}

function updateRestartCommandMode() {
    const manual = document.getElementById("serviceManualRestartEnabled").checked;
    const restartCommand = document.getElementById("serviceRestartCommand");
    restartCommand.disabled = !manual;
    restartCommand.required = manual;
}

async function requestJson(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        throw await createHttpError(response);
    }
    if (response.status === 204) {
        return null;
    }
    return response.json();
}

async function requestVoid(url, options = {}) {
    const response = await fetch(url, options);
    if (!response.ok) {
        throw await createHttpError(response);
    }
}

async function createHttpError(response) {
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        const payload = await response.json();
        return new Error(payload.message || payload.error || `HTTP ${response.status}`);
    }
    return new Error(await response.text() || `HTTP ${response.status}`);
}

function handleError(error) {
    console.error(error);
    showToast(error.message || "Произошла ошибка.", true);
}

function showToast(message, isError = false) {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.classList.remove("hidden", "error");
    if (isError) {
        toast.classList.add("error");
    }

    clearTimeout(showToast.timeoutId);
    showToast.timeoutId = window.setTimeout(() => {
        toast.classList.add("hidden");
    }, 3200);
}

function getSelectedValues(select) {
    return Array.from(select.selectedOptions).map((option) => option.value);
}

function clearMultiSelect(select) {
    Array.from(select.options).forEach((option) => {
        option.selected = false;
    });
}

function renderBooleanPill(value) {
    return `<span class="boolean-pill ${value ? "is-true" : "is-false"}">${value ? "yes" : "no"}</span>`;
}

function renderHealthCheckPill(service) {
    if (!service.healthCheckEnabled) {
        return `<span class="boolean-pill health-off">off</span>`;
    }
    return renderBooleanPill(service.healthCheckPassed);
}

function formatDateTime(value) {
    if (!value) {
        return "—";
    }
    return new Date(value).toLocaleString("ru-RU");
}

function escapeHtml(value) {
    return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}
