const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

function createClassList() {
    const values = new Set();
    return {
        add: (...items) => items.forEach((item) => values.add(item)),
        remove: (...items) => items.forEach((item) => values.delete(item)),
        contains: (item) => values.has(item),
        toggle: (item, enabled) => {
            if (enabled) {
                values.add(item);
            } else {
                values.delete(item);
            }
        }
    };
}

function createElement(id = "") {
    const attributes = new Map();
    return {
        id,
        value: "",
        checked: false,
        disabled: false,
        innerHTML: "",
        textContent: "",
        dataset: {},
        style: {},
        scrollHeight: 480,
        offsetTop: 0,
        selectedOptions: [],
        options: [],
        classList: createClassList(),
        addEventListener: () => {},
        removeAttribute: (name) => attributes.delete(name),
        reset: () => {},
        setAttribute: (name, value) => attributes.set(name, String(value)),
        getAttribute: (name) => attributes.get(name),
        querySelector: () => null
    };
}

function jsonResponse(payload) {
    return {
        ok: true,
        status: 200,
        headers: {
            get: () => "application/json"
        },
        json: async () => payload
    };
}

function emptyResponse() {
    return {
        ok: true,
        status: 204,
        headers: {
            get: () => ""
        }
    };
}

function deferredResponse() {
    let resolve;
    const promise = new Promise((done) => {
        resolve = done;
    });
    return {promise, resolve};
}

function loadApp(fetchImpl = async () => jsonResponse({summary: emptySummary(), services: []})) {
    const elements = new Map();
    const document = {
        addEventListener: () => {},
        getElementById: (id) => {
            if (!elements.has(id)) {
                elements.set(id, createElement(id));
            }
            return elements.get(id);
        },
        querySelectorAll: () => []
    };

    const window = {
        confirm: () => true,
        addEventListener: () => {},
        clearInterval: () => {},
        clearTimeout: () => {},
        scrollTo: () => {},
        setInterval: () => 1,
        setTimeout: () => 1
    };

    const context = {
        clearTimeout: window.clearTimeout,
        console,
        document,
        fetch: fetchImpl,
        setTimeout: window.setTimeout,
        URLSearchParams,
        window
    };
    context.globalThis = context;
    vm.createContext(context);
    vm.runInContext(
        fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/app.js"), "utf8"),
        context
    );

    return {context, elements};
}

function emptySummary() {
    return {
        total: 0,
        up: 0,
        down: 0,
        paused: 0,
        restarting: 0,
        error: 0,
        unknown: 0
    };
}

test("dashboard polling refreshes only dashboard data on a short interval", async () => {
    const {context} = loadApp();
    const scheduled = [];

    context.window.setInterval = (callback, delay) => {
        scheduled.push({callback, delay});
        return 42;
    };
    context.window.clearInterval = () => {};

    vm.runInContext(`
        globalThis.dashboardCalls = 0;
        globalThis.hostCalls = 0;
        globalThis.groupCalls = 0;
        loadDashboard = async () => { globalThis.dashboardCalls += 1; };
        loadHosts = async () => { globalThis.hostCalls += 1; };
        loadGroups = async () => { globalThis.groupCalls += 1; };
        startDashboardPolling();
    `, context);

    assert.equal(scheduled.length, 1);
    assert.equal(scheduled[0].delay, 5000);

    await scheduled[0].callback();

    assert.equal(context.dashboardCalls, 1);
    assert.equal(context.hostCalls, 0);
    assert.equal(context.groupCalls, 0);
});

test("service actions keep the clicked button busy until the operation and dashboard refresh finish", async () => {
    const restart = deferredResponse();
    const dashboard = deferredResponse();
    const calls = [];
    const {context} = loadApp((url) => {
        calls.push(url);
        if (url === "/api/services/7/restart") {
            return restart.promise;
        }
        if (url === "/api/dashboard") {
            return dashboard.promise;
        }
        return Promise.resolve(jsonResponse([]));
    });

    const button = createElement("restartButton");
    button.dataset = {action: "restart", id: "7"};
    button.closest = (selector) => selector === "button[data-action]" ? button : null;

    const operation = context.onServicesTableClick({target: button});

    assert.equal(button.disabled, true);
    assert.equal(button.classList.contains("is-loading"), true);
    assert.equal(button.getAttribute("aria-busy"), "true");

    restart.resolve(emptyResponse());
    await Promise.resolve();
    assert.equal(button.disabled, true);

    dashboard.resolve(jsonResponse({summary: emptySummary(), services: []}));
    await operation;

    assert.deepEqual(calls, ["/api/services/7/restart", "/api/dashboard"]);
    assert.equal(button.disabled, false);
    assert.equal(button.classList.contains("is-loading"), false);
    assert.equal(button.getAttribute("aria-busy"), undefined);
});

test("save buttons stay busy until save and post-save refresh finish", async () => {
    const save = deferredResponse();
    const dashboard = deferredResponse();
    const calls = [];
    const {context} = loadApp((url, options = {}) => {
        calls.push({url, options});
        if (url === "/api/services") {
            return save.promise;
        }
        if (url === "/api/dashboard") {
            return dashboard.promise;
        }
        return Promise.resolve(jsonResponse([]));
    });
    const element = (id) => context.document.getElementById(id);

    element("serviceName").value = "billing";
    element("serviceHostId").value = "1";
    element("serviceGroupId").value = "";
    element("serviceProcessMatch").value = "billing.jar";
    element("serviceExecutionPath").value = "/opt/billing";
    element("serviceStartCommand").value = "./start.sh";
    element("serviceManualRestartEnabled").checked = true;
    element("serviceRestartCommand").value = "restart billing";
    element("serviceHealthUrl").value = "";
    element("serviceHealthTimeoutSeconds").value = "3";
    element("serviceRestartCooldownSeconds").value = "60";
    element("serviceRestartWindowSeconds").value = "600";
    element("serviceMaxRestartsInWindow").value = "3";
    element("serviceMonitoringEnabled").checked = true;
    element("serviceNotificationsEnabled").checked = true;
    element("serviceDescription").value = "";

    const button = createElement("saveButton");
    const form = createElement("serviceForm");
    form.querySelector = () => button;

    const operation = context.submitServiceForm({
        preventDefault: () => {},
        currentTarget: form,
        submitter: button
    });

    assert.equal(button.disabled, true);
    assert.equal(button.classList.contains("is-loading"), true);

    save.resolve(jsonResponse({id: 7}));
    await Promise.resolve();
    assert.equal(button.disabled, true);

    dashboard.resolve(jsonResponse({summary: emptySummary(), services: []}));
    await operation;

    assert.deepEqual(calls.map((call) => call.url), ["/api/services", "/api/dashboard"]);
    assert.deepEqual(JSON.parse(calls[0].options.body), {
        name: "billing",
        hostId: 1,
        groupId: null,
        processMatch: "billing.jar",
        executionPath: "/opt/billing",
        startCommand: "./start.sh",
        manualRestartEnabled: true,
        restartCommand: "restart billing",
        healthUrl: null,
        healthTimeoutSeconds: 3,
        restartCooldownSeconds: 60,
        restartWindowSeconds: 600,
        maxRestartsInWindow: 3,
        monitoringEnabled: true,
        notificationsEnabled: true,
        description: null
    });
    assert.equal(button.disabled, false);
    assert.equal(button.classList.contains("is-loading"), false);
});

test("notification controls follow the feature flag from the dashboard payload", () => {
    const {context} = loadApp();
    const element = (id) => context.document.getElementById(id);
    const servicesBody = element("servicesTableBody");
    const serviceRow = {
        id: 7,
        name: "billing",
        hostName: "local",
        hostAddress: "127.0.0.1",
        groupName: null,
        monitoringEnabled: true,
        notificationsEnabled: false,
        status: "UP",
        processRunning: true,
        lastKnownPid: 1234,
        healthCheckEnabled: false,
        healthCheckPassed: false,
        lastCheckAt: null,
        lastMessage: "ok"
    };

    vm.runInContext(`state.notifications = {featureEnabled: false, globalEnabled: true};`, context);
    context.updateNotificationControls();
    context.renderServicesTable([serviceRow]);

    assert.equal(element("toggleNotificationsButton").classList.contains("hidden"), true);
    assert.equal(element("serviceNotificationsField").classList.contains("hidden"), true);
    assert.doesNotMatch(servicesBody.innerHTML, /data-action="toggle-notifications"/);
    assert.doesNotMatch(servicesBody.innerHTML, /alerts off/);

    vm.runInContext(`state.notifications = {featureEnabled: true, globalEnabled: false};`, context);
    context.updateNotificationControls();
    context.renderServicesTable([serviceRow]);

    assert.equal(element("toggleNotificationsButton").classList.contains("hidden"), false);
    assert.equal(element("toggleNotificationsButton").classList.contains("notifications-off"), true);
    assert.equal(element("toggleNotificationsButton").textContent, "Оповещения: выкл");
    assert.equal(element("serviceNotificationsField").classList.contains("hidden"), false);
    assert.match(servicesBody.innerHTML, /data-action="toggle-notifications"/);
    assert.match(servicesBody.innerHTML, /Вкл\. оповещения/);
    assert.match(servicesBody.innerHTML, /alerts off/);
});

test("dashboard renders pid and off for services without health-check url", () => {
    const {context} = loadApp();
    const tbody = context.document.getElementById("servicesTableBody");

    context.renderServicesTable([{
        id: 7,
        name: "billing",
        hostName: "local",
        hostAddress: "127.0.0.1",
        groupName: null,
        monitoringEnabled: true,
        status: "UP",
        processRunning: true,
        lastKnownPid: 1234,
        healthCheckEnabled: false,
        healthCheckPassed: false,
        lastCheckAt: null,
        lastMessage: "ok"
    }]);

    assert.match(tbody.innerHTML, />1234</);
    assert.match(tbody.innerHTML, /health-off/);
    assert.match(tbody.innerHTML, />off</);
});

test("service host and group actions render under hamburger menus", () => {
    const {context} = loadApp();
    const servicesBody = context.document.getElementById("servicesTableBody");
    const hostsBody = context.document.getElementById("hostsTableBody");
    const groupsBody = context.document.getElementById("groupsTableBody");

    context.renderServicesTable([{
        id: 7,
        name: "billing",
        hostName: "local",
        hostAddress: "127.0.0.1",
        groupName: "core",
        monitoringEnabled: true,
        status: "UP",
        processRunning: true,
        lastKnownPid: 1234,
        healthCheckEnabled: true,
        healthCheckPassed: true,
        lastCheckAt: null,
        lastMessage: "ok"
    }]);
    vm.runInContext(`
        state.hosts = [{id: 3, name: "local", connectionMode: "LOCAL", address: "127.0.0.1"}];
        state.groups = [{id: 5, name: "core", description: "main"}];
        renderHostsTable();
        renderGroupsTable();
    `, context);

    assert.match(servicesBody.innerHTML, /class="hamburger-button"/);
    assert.match(servicesBody.innerHTML, /class="[^"]*action-menu/);
    assert.match(servicesBody.innerHTML, /data-action="restart"/);
    assert.doesNotMatch(servicesBody.innerHTML, /class="actions"/);

    assert.match(hostsBody.innerHTML, /class="hamburger-button"/);
    assert.match(hostsBody.innerHTML, /data-action="edit-host"/);
    assert.match(groupsBody.innerHTML, /class="hamburger-button"/);
    assert.match(groupsBody.innerHTML, /data-action="edit-group"/);
});

test("open action menu button state survives dashboard table refresh", () => {
    const {context} = loadApp();
    const servicesBody = context.document.getElementById("servicesTableBody");

    vm.runInContext(`state.openActionMenuId = "service-7";`, context);
    context.renderServicesTable([{
        id: 7,
        name: "billing",
        hostName: "local",
        hostAddress: "127.0.0.1",
        groupName: "core",
        monitoringEnabled: true,
        status: "UP",
        processRunning: true,
        lastKnownPid: 1234,
        healthCheckEnabled: true,
        healthCheckPassed: true,
        lastCheckAt: null,
        lastMessage: "ok"
    }]);

    assert.match(servicesBody.innerHTML, /data-menu-id="service-7"/);
    assert.match(servicesBody.innerHTML, /aria-expanded="true"/);
});

test("floating tab button switches between dashboard and configuration with swipe state", () => {
    const {context} = loadApp();
    const element = (id) => context.document.getElementById(id);

    element("dashboardTab").scrollHeight = 320;
    element("configurationTab").scrollHeight = 840;

    context.initializeTabs();

    assert.equal(element("tabsTrack").classList.contains("is-configuration"), false);
    assert.equal(element("dashboardTab").getAttribute("aria-hidden"), "false");
    assert.equal(element("configurationTab").getAttribute("aria-hidden"), "true");
    assert.equal(element("tabsViewport").style.height, "320px");
    assert.equal(element("tabSwitchIcon").textContent, ">");

    context.toggleActiveTab();

    assert.equal(element("tabsTrack").classList.contains("is-configuration"), true);
    assert.equal(element("dashboardTab").getAttribute("aria-hidden"), "true");
    assert.equal(element("configurationTab").getAttribute("aria-hidden"), "false");
    assert.equal(element("tabsViewport").style.height, "840px");
    assert.equal(element("tabSwitchButton").classList.contains("is-back"), true);
    assert.equal(element("tabSwitchButton").getAttribute("aria-label"), "Открыть dashboard");
    assert.equal(element("tabSwitchIcon").textContent, "<");

    context.toggleActiveTab();

    assert.equal(element("tabsTrack").classList.contains("is-configuration"), false);
    assert.equal(element("dashboardTab").getAttribute("aria-hidden"), "false");
    assert.equal(element("configurationTab").getAttribute("aria-hidden"), "true");
});
