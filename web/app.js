const STORAGE_KEYS = {
  expenses: "expense-tracker:data",
  apiKey: "expense-tracker:openai-key",
  analysisHistory: "expense-tracker:analysis-history",
  pending: "expense-tracker:pending-queue",
};

const MAX_PENDING_FILE_SIZE = 5 * 1024 * 1024; // 5 MB to keep localStorage manageable

const form = document.querySelector("#expense-form");
const tableBody = document.querySelector("#expense-rows");
const viewSelect = document.querySelector("#view-select");
const chartTabs = document.querySelectorAll(".chart-tab");
const chartCanvas = document.querySelector("#expenses-chart");
const apiKeyForm = document.querySelector("#api-key-form");
const apiKeyInput = document.querySelector("#api-key-input");
const clearApiKeyButton = document.querySelector("#clear-api-key");
const toggleApiKeyButton = document.querySelector("#toggle-api-key");
const connectionStatus = document.querySelector("#connection-status");
const fileForm = document.querySelector("#file-form");
const fileInput = document.querySelector("#file-input");
const analysisMessages = document.querySelector("#analysis-messages");
const analysisHistoryList = document.querySelector("#analysis-history");

const todayISO = new Date().toISOString().split("T")[0];
document.querySelector("#expense-date").value = todayISO;

let expenses = loadExpenses();
let chartView = "monthly";
let chartInstance = null;
let apiKey = loadApiKey();
let analysisHistory = loadAnalysisHistory();
let pendingAnalyses = loadPendingAnalyses();
let isOnline = navigator.onLine;
let apiKeyVisible = false;

initialize();

function initialize() {
  render();
  renderAnalysisHistory();
  updateApiKeyState();
  updateConnectionStatus();

  form.addEventListener("submit", handleExpenseSubmit);
  viewSelect.addEventListener("change", renderTable);
  chartTabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      chartTabs.forEach((t) => t.classList.remove("active"));
      tab.classList.add("active");
      chartView = tab.dataset.view;
      renderChart();
    });
  });

  apiKeyForm.addEventListener("submit", (event) => {
    event.preventDefault();
    apiKey = apiKeyInput.value.trim();
    if (!apiKey) {
      showMessage("Įveskite galiojantį API raktą.", "error");
      return;
    }
    localStorage.setItem(STORAGE_KEYS.apiKey, apiKey);
    updateApiKeyState();
    showMessage("API raktas išsaugotas.", "success");
    if (isOnline) {
      processPendingAnalyses();
    }
  });

  clearApiKeyButton.addEventListener("click", () => {
    localStorage.removeItem(STORAGE_KEYS.apiKey);
    apiKey = null;
    updateApiKeyState();
    showMessage("API raktas pašalintas.", "info");
  });

  toggleApiKeyButton.addEventListener("click", () => {
    apiKeyVisible = !apiKeyVisible;
    apiKeyInput.type = apiKeyVisible ? "text" : "password";
    toggleApiKeyButton.textContent = apiKeyVisible ? "Slėpti" : "Rodyti";
  });

  fileForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const files = Array.from(fileInput.files ?? []);
    if (files.length === 0) {
      showAnalysisMessage("Pasirinkite bent vieną failą.", "warning");
      return;
    }
    for (const file of files) {
      await handleFileForAnalysis(file);
    }
    fileInput.value = "";
  });

  window.addEventListener("online", () => {
    isOnline = true;
    updateConnectionStatus();
    processPendingAnalyses();
  });

  window.addEventListener("offline", () => {
    isOnline = false;
    updateConnectionStatus();
  });

  if (isOnline && apiKey) {
    processPendingAnalyses();
  }
}

function handleExpenseSubmit(event) {
  event.preventDefault();
  const formData = new FormData(form);
  const expense = {
    id: crypto.randomUUID(),
    date: formData.get("date"),
    category: formData.get("category"),
    description: formData.get("description"),
    amount: parseFloat(formData.get("amount")),
  };

  expenses = [expense, ...expenses];
  persistExpenses(expenses);
  form.reset();
  document.querySelector("#expense-date").value = todayISO;
  render();
  showMessage("Įrašas išsaugotas (saugoma įrenginyje).", "success");
}

function loadExpenses() {
  const stored = localStorage.getItem(STORAGE_KEYS.expenses);
  if (stored) {
    try {
      const parsed = JSON.parse(stored);
      if (Array.isArray(parsed)) {
        return parsed;
      }
    } catch (error) {
      console.error("Nepavyko nuskaityti išlaidų:", error);
    }
  }
  return [];
}

function persistExpenses(list) {
  localStorage.setItem(STORAGE_KEYS.expenses, JSON.stringify(list));
}

function loadApiKey() {
  return localStorage.getItem(STORAGE_KEYS.apiKey);
}

function loadAnalysisHistory() {
  const stored = localStorage.getItem(STORAGE_KEYS.analysisHistory);
  if (!stored) return [];
  try {
    const parsed = JSON.parse(stored);
    if (Array.isArray(parsed)) {
      return parsed;
    }
  } catch (error) {
    console.error("Nepavyko nuskaityti analizės istorijos", error);
  }
  return [];
}

function persistAnalysisHistory(history) {
  localStorage.setItem(STORAGE_KEYS.analysisHistory, JSON.stringify(history.slice(0, 20)));
}

function loadPendingAnalyses() {
  const stored = localStorage.getItem(STORAGE_KEYS.pending);
  if (!stored) return [];
  try {
    const parsed = JSON.parse(stored);
    if (Array.isArray(parsed)) {
      return parsed;
    }
  } catch (error) {
    console.error("Nepavyko nuskaityti laukiančių failų", error);
  }
  return [];
}

function persistPendingAnalyses(queue) {
  localStorage.setItem(STORAGE_KEYS.pending, JSON.stringify(queue));
}

function render() {
  renderTable();
  renderChart();
}

function renderTable() {
  const view = viewSelect.value;
  const filtered = filterExpenses(expenses, view);

  tableBody.innerHTML = "";
  if (filtered.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="4" class="empty">Nėra duomenų pasirinktam laikotarpiui.</td></tr>`;
    return;
  }

  const formatter = new Intl.NumberFormat("lt-LT", {
    style: "currency",
    currency: "EUR",
    maximumFractionDigits: 2,
  });

  const dateFormatter = new Intl.DateTimeFormat("lt-LT", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });

  const rows = filtered
    .sort((a, b) => new Date(b.date) - new Date(a.date))
    .map((expense) => {
      return `<tr>
          <td>${dateFormatter.format(new Date(expense.date))}</td>
          <td>${expense.category}</td>
          <td>${expense.description}</td>
          <td class="numeric">${formatter.format(expense.amount)}</td>
        </tr>`;
    })
    .join("");

  tableBody.innerHTML = rows;
}

function filterExpenses(list, view) {
  const now = new Date();
  if (view === "monthly") {
    return list.filter((expense) => {
      const date = new Date(expense.date);
      return date.getMonth() === now.getMonth() && date.getFullYear() === now.getFullYear();
    });
  }

  if (view === "yearly") {
    return list.filter((expense) => new Date(expense.date).getFullYear() === now.getFullYear());
  }

  return list;
}

function renderChart() {
  const context = chartCanvas.getContext("2d");
  const data = chartView === "monthly" ? buildMonthlyData() : buildYearlyData();

  if (chartInstance) {
    chartInstance.destroy();
  }

  chartInstance = new Chart(context, {
    type: chartView === "monthly" ? "bar" : "line",
    data,
    options: {
      responsive: true,
      scales: {
        y: {
          beginAtZero: true,
        },
      },
      plugins: {
        legend: { display: false },
      },
    },
  });
}

function buildMonthlyData() {
  const now = new Date();
  const expensesThisMonth = filterExpenses(expenses, "monthly");
  const byCategory = expensesThisMonth.reduce((acc, expense) => {
    acc[expense.category] = (acc[expense.category] ?? 0) + expense.amount;
    return acc;
  }, {});

  return {
    labels: Object.keys(byCategory),
    datasets: [
      {
        label: `${now.toLocaleString("lt-LT", { month: "long" })} mėn.`,
        data: Object.values(byCategory),
        backgroundColor: "rgba(68, 99, 255, 0.6)",
        borderRadius: 12,
      },
    ],
  };
}

function buildYearlyData() {
  const byMonth = Array.from({ length: 12 }, (_, index) => ({ month: index, total: 0 }));
  expenses.forEach((expense) => {
    const date = new Date(expense.date);
    if (date.getFullYear() === new Date().getFullYear()) {
      byMonth[date.getMonth()].total += expense.amount;
    }
  });

  return {
    labels: byMonth.map((entry) => new Date(0, entry.month).toLocaleString("lt-LT", { month: "short" })),
    datasets: [
      {
        label: "Metinės išlaidos",
        data: byMonth.map((entry) => entry.total),
        borderColor: "rgba(68, 99, 255, 0.7)",
        backgroundColor: "rgba(68, 99, 255, 0.15)",
        fill: true,
        tension: 0.3,
      },
    ],
  };
}

function updateApiKeyState() {
  apiKeyInput.value = apiKey ?? "";
  clearApiKeyButton.disabled = !apiKey;
}

function updateConnectionStatus() {
  const message = isOnline ? "Prisijungta prie interneto" : "Veikia offline režimu";
  connectionStatus.textContent = message;
  connectionStatus.dataset.state = isOnline ? "online" : "offline";
}

async function handleFileForAnalysis(file) {
  if (file.size > MAX_PENDING_FILE_SIZE && (!isOnline || !apiKey)) {
    showAnalysisMessage(
      `${file.name} viršija ${MAX_PENDING_FILE_SIZE / (1024 * 1024)}MB limitą offline saugojimui. Prisijunkite ir bandykite iškart apdoroti.`,
      "error"
    );
    return;
  }

  if (!apiKey) {
    await saveFileForLater(file);
    showAnalysisMessage("Prieš analizę įveskite API raktą. Failas išsaugotas laukiančių sąraše.", "warning");
    return;
  }

  if (!isOnline) {
    await saveFileForLater(file);
    showAnalysisMessage("Esate offline – failas bus išanalizuotas prisijungus.", "info");
    return;
  }

  await analyzeFileNow(file);
}

async function analyzeFileNow(file) {
  const statusId = crypto.randomUUID();
  const statusElement = appendAnalysisStatus(`${file.name} – analizuojama...`, statusId);
  try {
    const summary = await sendFileToOpenAI(file, apiKey);
    addAnalysisHistory({
      id: crypto.randomUUID(),
      fileName: file.name,
      summary,
      analyzedAt: Date.now(),
    });
    replaceAnalysisStatus(statusElement, `${file.name} – analizė atlikta.`, "success");
  } catch (error) {
    console.error(error);
    replaceAnalysisStatus(statusElement, `${file.name} – klaida: ${error.message}`, "error");
  }
}

async function saveFileForLater(file) {
  const dataUrl = await fileToDataUrl(file);
  pendingAnalyses = [
    {
      id: crypto.randomUUID(),
      name: file.name,
      type: file.type,
      size: file.size,
      dataUrl,
    },
    ...pendingAnalyses,
  ];
  persistPendingAnalyses(pendingAnalyses.slice(0, 10));
}

async function processPendingAnalyses() {
  if (!isOnline || !apiKey || pendingAnalyses.length === 0) {
    return;
  }

  const queue = [...pendingAnalyses];
  pendingAnalyses = [];
  persistPendingAnalyses(pendingAnalyses);

  for (const pending of queue.reverse()) {
    const file = dataUrlToFile(pending.dataUrl, pending.name, pending.type);
    await analyzeFileNow(file);
  }
}

async function sendFileToOpenAI(file, apiKeyValue) {
  const formData = new FormData();
  formData.append("file", file);
  formData.append("purpose", "assistants");

  const uploadResponse = await fetch("https://api.openai.com/v1/files", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKeyValue}`,
      "OpenAI-Beta": "assistants=v2",
    },
    body: formData,
  });

  if (!uploadResponse.ok) {
    throw new Error(`Įkėlimo klaida (${uploadResponse.status})`);
  }
  const uploaded = await uploadResponse.json();

  const responsePayload = {
    model: "gpt-4.1-mini",
    input: [
      {
        role: "user",
        content: [
          {
            type: "input_text",
            text: "Analizuok pridėtą išlaidų failą ir pateik svarbiausias įžvalgas lietuviškai.",
          },
          {
            type: "input_file",
            file_id: uploaded.id,
          },
        ],
      },
    ],
  };

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKeyValue}`,
      "Content-Type": "application/json",
      "OpenAI-Beta": "assistants=v2",
    },
    body: JSON.stringify(responsePayload),
  });

  if (!response.ok) {
    throw new Error(`Analizės klaida (${response.status})`);
  }
  const responseBody = await response.json();
  const output = responseBody.output ?? [];
  for (const entry of output) {
    const text = entry.content?.find((item) => item.type === "output_text");
    if (text) {
      return text.text.value;
    }
  }
  throw new Error("OpenAI nepateikė rezultatų");
}

function addAnalysisHistory(entry) {
  analysisHistory = [entry, ...analysisHistory].slice(0, 20);
  persistAnalysisHistory(analysisHistory);
  renderAnalysisHistory();
}

function renderAnalysisHistory() {
  analysisHistoryList.innerHTML = "";
  if (analysisHistory.length === 0) {
    analysisHistoryList.innerHTML = `<li class="muted">Analizės rezultatų dar nėra.</li>`;
    return;
  }

  const formatter = new Intl.DateTimeFormat("lt-LT", {
    dateStyle: "medium",
    timeStyle: "short",
  });

  analysisHistory.forEach((entry) => {
    const item = document.createElement("li");
    const title = document.createElement("strong");
    title.textContent = entry.fileName;
    const time = document.createElement("small");
    time.textContent = formatter.format(new Date(entry.analyzedAt));
    const summary = document.createElement("p");
    summary.textContent = entry.summary;
    item.appendChild(title);
    item.appendChild(document.createElement("br"));
    item.appendChild(time);
    item.appendChild(summary);
    analysisHistoryList.appendChild(item);
  });
}

function showMessage(message, type = "info") {
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  document.body.appendChild(toast);
  requestAnimationFrame(() => toast.classList.add("visible"));
  setTimeout(() => {
    toast.classList.remove("visible");
    setTimeout(() => toast.remove(), 300);
  }, 4000);
}

function showAnalysisMessage(message, type = "info") {
  const statusElement = appendAnalysisStatus(message, crypto.randomUUID());
  statusElement.dataset.state = type;
}

function appendAnalysisStatus(message, id) {
  const item = document.createElement("div");
  item.className = "analysis-status";
  item.dataset.id = id;
  item.textContent = message;
  analysisMessages.prepend(item);
  return item;
}

function replaceAnalysisStatus(element, message, type) {
  if (!element) return;
  element.textContent = message;
  element.dataset.state = type;
}

function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function dataUrlToFile(dataUrl, fileName, mimeType) {
  const [meta, base64Data] = dataUrl.split(",");
  const binary = atob(base64Data);
  const length = binary.length;
  const bytes = new Uint8Array(length);
  for (let i = 0; i < length; i += 1) {
    bytes[i] = binary.charCodeAt(i);
  }
  return new File([bytes], fileName, { type: mimeType });
}
