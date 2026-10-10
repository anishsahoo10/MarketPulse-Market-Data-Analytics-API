document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements
    const symbolInput = document.getElementById('symbolInput');
    const loadBtn = document.getElementById('loadBtn');
    const syncBtn = document.getElementById('syncBtn');
    const toast = document.getElementById('toastMessage');
    const healthBadge = document.getElementById('systemHealthBadge');
    const healthText = document.getElementById('systemHealthText');

    // Instrument Elements
    const instrumentName = document.getElementById('instrumentName');
    const instrumentSymbol = document.getElementById('instrumentSymbol');
    const instrumentExchange = document.getElementById('instrumentExchange');
    const instrumentCurrency = document.getElementById('instrumentCurrency');
    const instrumentUpdated = document.getElementById('instrumentUpdated');

    // Daily Change Elements
    const currentClose = document.getElementById('currentClose');
    const prevClose = document.getElementById('prevClose');
    const tradingDate = document.getElementById('tradingDate');
    const prevTradingDate = document.getElementById('prevTradingDate');
    const priceDeltaBadge = document.getElementById('priceDeltaBadge');
    const priceDeltaAbs = document.getElementById('priceDeltaAbs');
    const priceDeltaPct = document.getElementById('priceDeltaPct');

    // SMA Elements
    const smaValue = document.getElementById('smaValue');
    const smaMeta = document.getElementById('smaMeta');
    const customPeriodInput = document.getElementById('customPeriod');
    const calcSmaBtn = document.getElementById('calcSmaBtn');

    // Table & Chart Elements
    const pricesTableBody = document.getElementById('pricesTableBody');
    const recordCount = document.getElementById('recordCount');

    // Inspector
    const inspectUrlInstrument = document.getElementById('inspectUrlInstrument');
    const inspectJsonInstrument = document.getElementById('inspectJsonInstrument');
    const inspectUrlAnalytics = document.getElementById('inspectUrlAnalytics');
    const inspectJsonAnalytics = document.getElementById('inspectJsonAnalytics');

    let priceChart = null;
    let fullPriceHistory = [];
    let currentActiveRange = 90; // Default 3M

    // Initial Health Check
    checkSystemHealth();

    // Event Listeners
    loadBtn.addEventListener('click', () => loadAllData(symbolInput.value));
    syncBtn.addEventListener('click', () => syncProviderData(symbolInput.value));
    calcSmaBtn.addEventListener('click', () => {
        const period = parseInt(customPeriodInput.value, 10);
        if (period >= 2 && period <= 200) {
            calculateSMA(symbolInput.value, period);
        } else {
            showToast('Period must be between 2 and 200 sessions', 'error');
        }
    });

    // Quick Watchlist Ticker Chips
    document.querySelectorAll('.ticker-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            document.querySelectorAll('.ticker-chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');
            symbolInput.value = chip.dataset.symbol;
            loadAllData(chip.dataset.symbol);
        });
    });

    // SMA Period Pills
    document.querySelectorAll('.p-pill').forEach(pill => {
        pill.addEventListener('click', () => {
            document.querySelectorAll('.p-pill').forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            const period = parseInt(pill.dataset.period, 10);
            customPeriodInput.value = period;
            calculateSMA(symbolInput.value, period);
        });
    });

    // Range Selector Pills
    document.querySelectorAll('.range-pill').forEach(pill => {
        pill.addEventListener('click', () => {
            document.querySelectorAll('.range-pill').forEach(p => p.classList.remove('active'));
            pill.classList.add('active');
            currentActiveRange = pill.dataset.range === 'all' ? 'all' : parseInt(pill.dataset.range, 10);
            renderChartWithRange();
        });
    });

    symbolInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            loadAllData(symbolInput.value);
        }
    });

    // Initial startup: AAPL
    initialStartupSync('AAPL');

    async function initialStartupSync(symbol) {
        showToast('Initializing market data feed...', 'success');
        try {
            await fetch(`/api/v1/instruments/${symbol}/sync`, { method: 'POST' });
            loadAllData(symbol);
        } catch (e) {
            loadAllData(symbol);
        }
    }

    async function checkSystemHealth() {
        try {
            const res = await fetch('/actuator/health');
            if (res.ok) {
                const data = await res.json();
                healthBadge.className = 'system-status';
                healthText.textContent = `API ${data.status}`;
            } else {
                throw new Error();
            }
        } catch (e) {
            healthBadge.className = 'system-status error';
            healthText.textContent = 'API OFFLINE';
        }
    }

    async function loadAllData(symbol) {
        const cleanSymbol = (symbol || '').trim().toUpperCase();
        if (!cleanSymbol) {
            showToast('Please enter a valid ticker symbol', 'error');
            return;
        }

        symbolInput.value = cleanSymbol;

        // Sync active state in ribbon chips if matching
        document.querySelectorAll('.ticker-chip').forEach(c => {
            if (c.dataset.symbol === cleanSymbol) {
                c.classList.add('active');
            } else {
                c.classList.remove('active');
            }
        });

        inspectUrlInstrument.textContent = `/api/v1/instruments/${cleanSymbol}`;
        inspectUrlAnalytics.textContent = `/api/v1/instruments/${cleanSymbol}/analytics/daily-change`;

        await Promise.all([
            fetchInstrument(cleanSymbol),
            fetchPrices(cleanSymbol),
            fetchDailyChange(cleanSymbol),
            calculateSMA(cleanSymbol, parseInt(customPeriodInput.value, 10) || 20)
        ]);
    }

    async function fetchInstrument(symbol) {
        try {
            const res = await fetch(`/api/v1/instruments/${symbol}`);
            if (res.ok) {
                const data = await res.json();
                instrumentName.textContent = data.name || symbol;
                instrumentSymbol.textContent = data.symbol;
                instrumentExchange.textContent = data.exchange || 'STANDARD';
                instrumentCurrency.textContent = data.currency || 'USD';
                instrumentUpdated.textContent = data.updatedAt ? new Date(data.updatedAt).toLocaleDateString() : 'Active';
                inspectJsonInstrument.textContent = JSON.stringify(data, null, 2);
            } else if (res.status === 404) {
                instrumentName.textContent = 'Symbol Unindexed';
                instrumentSymbol.textContent = symbol;
                instrumentExchange.textContent = '-';
                instrumentCurrency.textContent = '-';
                instrumentUpdated.textContent = '-';
                inspectJsonInstrument.textContent = `// 404 INSTRUMENT_NOT_FOUND\n// Click "Sync Provider" to import this instrument into PostgreSQL.`;
                showToast(`Asset '${symbol}' not in local records. Click 'Sync Provider' to import.`, 'error');
            }
        } catch (e) {
            console.error('Failed to query instrument:', e);
        }
    }

    async function fetchPrices(symbol) {
        try {
            const res = await fetch(`/api/v1/instruments/${symbol}/prices`);
            if (res.ok) {
                const data = await res.json();
                fullPriceHistory = data.prices || [];
                recordCount.textContent = `${data.count} sessions`;
                renderPricesTable(fullPriceHistory);
                renderChartWithRange();
            } else {
                fullPriceHistory = [];
                recordCount.textContent = '0 sessions';
                pricesTableBody.innerHTML = `<tr><td colspan="6" class="table-empty">No price data stored for ${symbol}. Click "Sync Provider" to fetch history.</td></tr>`;
                if (priceChart) {
                    priceChart.destroy();
                    priceChart = null;
                }
            }
        } catch (e) {
            console.error('Failed to query prices:', e);
        }
    }

    async function fetchDailyChange(symbol) {
        try {
            const res = await fetch(`/api/v1/instruments/${symbol}/analytics/daily-change`);
            if (res.ok) {
                const data = await res.json();
                inspectJsonAnalytics.textContent = JSON.stringify(data, null, 2);

                tradingDate.textContent = `${data.currentTradingDate}`;
                prevTradingDate.textContent = data.previousTradingDate;
                currentClose.textContent = `$${parseFloat(data.currentClose).toFixed(2)}`;
                prevClose.textContent = `$${parseFloat(data.previousClose).toFixed(2)}`;

                const abs = parseFloat(data.absoluteChange);
                const pct = parseFloat(data.percentageChange);

                const isPositive = abs >= 0;
                priceDeltaBadge.className = `delta-tag ${isPositive ? 'positive' : 'negative'}`;
                priceDeltaAbs.textContent = `${isPositive ? '+' : ''}$${abs.toFixed(2)}`;
                priceDeltaPct.textContent = `(${isPositive ? '+' : ''}${pct.toFixed(2)}%)`;
            } else {
                tradingDate.textContent = '-';
                prevTradingDate.textContent = '-';
                currentClose.textContent = '$0.00';
                prevClose.textContent = '$0.00';
                priceDeltaBadge.className = 'delta-tag';
                priceDeltaAbs.textContent = '$0.00';
                priceDeltaPct.textContent = '(0.00%)';
                inspectJsonAnalytics.textContent = `// Insufficient session history for daily change analytics`;
            }
        } catch (e) {
            console.error('Failed to calculate daily change:', e);
        }
    }

    async function calculateSMA(symbol, period) {
        try {
            smaMeta.textContent = `Computing SMA(${period})...`;
            const res = await fetch(`/api/v1/instruments/${symbol}/analytics/moving-average?period=${period}`);
            if (res.ok) {
                const data = await res.json();
                smaValue.textContent = `$${parseFloat(data.simpleMovingAverage).toFixed(2)}`;
                smaMeta.textContent = `SMA(${data.period}) across ${data.dataPointsUsed} sessions (${data.asOfDate})`;
            } else {
                const err = await res.json();
                smaValue.textContent = 'N/A';
                smaMeta.textContent = err.message || `Minimum ${period} sessions required`;
            }
        } catch (e) {
            smaValue.textContent = 'N/A';
            smaMeta.textContent = 'Calculation failure';
        }
    }

    async function syncProviderData(symbol) {
        const cleanSymbol = (symbol || '').trim().toUpperCase();
        if (!cleanSymbol) return;

        const refreshIcon = syncBtn.querySelector('.refresh-icon');
        const btnLabel = syncBtn.querySelector('.btn-label');

        syncBtn.disabled = true;
        if (refreshIcon) refreshIcon.classList.add('spin');
        if (btnLabel) btnLabel.textContent = 'Syncing...';
        showToast(`Requesting historical feed for ${cleanSymbol}...`, 'success');

        try {
            const res = await fetch(`/api/v1/instruments/${cleanSymbol}/sync`, { method: 'POST' });
            const data = await res.json();

            if (res.ok) {
                showToast(`Sync complete for ${cleanSymbol}: ${data.recordsInserted} inserted, ${data.recordsUpdated} updated, ${data.recordsRejected} rejected.`, 'success');
                await loadAllData(cleanSymbol);
            } else {
                showToast(`Sync error: ${data.message || 'Provider communication failed'}`, 'error');
            }
        } catch (e) {
            showToast('Network error during provider synchronization', 'error');
        } finally {
            syncBtn.disabled = false;
            if (refreshIcon) refreshIcon.classList.remove('spin');
            if (btnLabel) btnLabel.textContent = 'Sync Provider';
        }
    }

    function renderPricesTable(prices) {
        if (!prices || prices.length === 0) {
            pricesTableBody.innerHTML = `<tr><td colspan="6" class="table-empty">No price records available.</td></tr>`;
            return;
        }

        // Show newest session first in ledger
        const reversed = [...prices].reverse();
        pricesTableBody.innerHTML = reversed.map(p => `
            <tr>
                <td><strong>${p.date}</strong></td>
                <td>$${p.open != null ? parseFloat(p.open).toFixed(2) : '-'}</td>
                <td>$${p.high != null ? parseFloat(p.high).toFixed(2) : '-'}</td>
                <td>$${p.low != null ? parseFloat(p.low).toFixed(2) : '-'}</td>
                <td><strong>$${parseFloat(p.close).toFixed(2)}</strong></td>
                <td>${p.volume != null ? Number(p.volume).toLocaleString() : '-'}</td>
            </tr>
        `).join('');
    }

    function renderChartWithRange() {
        if (!fullPriceHistory || fullPriceHistory.length === 0) return;

        let filtered = fullPriceHistory;
        if (currentActiveRange !== 'all') {
            const count = Math.min(filtered.length, currentActiveRange);
            filtered = filtered.slice(filtered.length - count);
        }

        const labels = filtered.map(p => p.date);
        const closes = filtered.map(p => parseFloat(p.close));
        const volumes = filtered.map(p => p.volume || 0);

        const ctx = document.getElementById('priceChart').getContext('2d');

        if (priceChart) {
            priceChart.destroy();
        }

        // Refined Light Mode Gradient
        const gradient = ctx.createLinearGradient(0, 0, 0, 300);
        gradient.addColorStop(0, 'rgba(37, 99, 235, 0.16)');
        gradient.addColorStop(1, 'rgba(37, 99, 235, 0.0)');

        priceChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Close Price ($)',
                        data: closes,
                        borderColor: '#2563eb',
                        backgroundColor: gradient,
                        borderWidth: 2,
                        fill: true,
                        tension: 0.16,
                        pointRadius: labels.length > 60 ? 0 : 2.5,
                        pointHoverRadius: 5,
                        pointBackgroundColor: '#2563eb',
                        pointBorderColor: '#ffffff',
                        pointBorderWidth: 1.5,
                        yAxisID: 'y'
                    },
                    {
                        label: 'Volume',
                        data: volumes,
                        type: 'bar',
                        backgroundColor: 'rgba(15, 23, 42, 0.06)',
                        borderColor: 'transparent',
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(0, 0, 0, 0.03)' },
                        ticks: { color: '#94a3b8', font: { family: 'JetBrains Mono', size: 10 } }
                    },
                    y: {
                        type: 'linear',
                        display: true,
                        position: 'left',
                        grid: { color: 'rgba(0, 0, 0, 0.04)' },
                        ticks: {
                            color: '#64748b',
                            font: { family: 'JetBrains Mono', size: 11 },
                            callback: value => '$' + value.toFixed(2)
                        }
                    },
                    y1: {
                        type: 'linear',
                        display: false,
                        position: 'right',
                        grid: { drawOnChartArea: false },
                        max: Math.max(...volumes) * 4.5
                    }
                },
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        backgroundColor: '#0f172a',
                        borderColor: '#1e293b',
                        borderWidth: 1,
                        titleColor: '#f8fafc',
                        bodyColor: '#93c5fd',
                        titleFont: { family: 'JetBrains Mono', size: 11 },
                        bodyFont: { family: 'JetBrains Mono', size: 12 },
                        padding: 10,
                        cornerRadius: 6
                    }
                }
            }
        });
    }

    function showToast(message, type = 'success') {
        toast.textContent = message;
        toast.className = `toast-bar ${type}`;
        setTimeout(() => {
            toast.className = 'toast-bar hidden';
        }, 4500);
    }
});
