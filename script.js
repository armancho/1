document.addEventListener('DOMContentLoaded', function () {
    const coinForm = document.getElementById('coinForm');
    const coinIdInput = document.getElementById('coinId');
    const contractAddressInput = document.getElementById('contractAddress');
    const networkInput = document.getElementById('network');
    const loadingElement = document.getElementById('loading');
    const progressElement = document.getElementById('progress');

    // Настройки по умолчанию
    let epochs = 20;
    let batchSize = 64;
    let neurons = 20; // Для LSTM/GRU/MLP
    let filters = 32; // Для CNN
    let kernelSize = 3; // Для CNN
    let forecastDays = 30; // Количество дней для прогноза

    // Поля для настройки параметров
    const epochsInput = document.getElementById('epochs');
    const batchSizeInput = document.getElementById('batchSize');
    const neuronsInput = document.getElementById('neurons');
    const filtersInput = document.getElementById('filters');
    const kernelSizeInput = document.getElementById('kernelSize');
    const forecastDaysInput = document.getElementById('forecastDays');

    // Установка значений по умолчанию
    if (epochsInput && batchSizeInput) {
        epochsInput.value = epochs;
        batchSizeInput.value = batchSize;
        if (neuronsInput) neuronsInput.value = neurons;
        if (filtersInput) filtersInput.value = filters;
        if (kernelSizeInput) kernelSizeInput.value = kernelSize;
        if (forecastDaysInput) forecastDaysInput.value = forecastDays;
    }

    // Обработчики для кнопок
    document.getElementById('toggleTrainingSettings')?.addEventListener('click', function () {
        const trainingSettings = document.getElementById('trainingSettings');
        trainingSettings.style.display = trainingSettings.style.display === 'none' ? 'block' : 'none';
    });

    document.getElementById('closeTrainingSettings')?.addEventListener('click', function () {
        epochs = parseInt(epochsInput.value, 10);
        batchSize = parseInt(batchSizeInput.value, 10);
        if (neuronsInput) neurons = parseInt(neuronsInput.value, 10);
        if (filtersInput) filters = parseInt(filtersInput.value, 10);
        if (kernelSizeInput) kernelSize = parseInt(kernelSizeInput.value, 10);
        if (forecastDaysInput) forecastDays = parseInt(forecastDaysInput.value, 10);
        document.getElementById('trainingSettings').style.display = 'none';
        console.log('Updated training settings:', { epochs, batchSize, neurons, filters, kernelSize, forecastDays });
    });

    // Выбор архитектуры модели
    const modelArchitecture = 'LSTM'; // Меняйте здесь на 'CNN', 'GRU', 'MLP', 'Hybrid'

    coinForm.addEventListener('submit', async function (e) {
        e.preventDefault();

        let coinId = coinIdInput.value.trim();
        const contractAddress = contractAddressInput.value.trim();
        const network = networkInput.value;

        if (coinId) {
            try {
                coinId = await getFullCryptoName(coinId);
            } catch (error) {
                console.error('Error fetching full crypto name:', error);
                alert('Failed to fetch cryptocurrency data. Please try again later.');
                return;
            }
        }

        if (coinId && contractAddress) {
            alert('Please enter either Coin ID or Contract Address, not both.');
            return;
        } else if (!coinId && !contractAddress) {
            alert('Please enter either Coin ID or Contract Address.');
            return;
        }

        if (contractAddress && !isValidContractAddress(contractAddress)) {
            alert('Invalid contract address. It should start with 0x and be 42 characters long.');
            return;
        }

        // Вызов функции прогнозирования
        fetchData(coinId, contractAddress, network, epochs, batchSize, neurons, filters, kernelSize, forecastDays, modelArchitecture);
    });

    // Функция для получения полного имени криптовалюты
    async function getFullCryptoName(coinId) {
        const response = await fetch('https://api.coingecko.com/api/v3/coins/list');
        if (!response.ok) throw new Error('Failed to fetch cryptocurrency list.');
        const data = await response.json();

        let coins = data.filter(coin => coin.name.toLowerCase() === coinId.toLowerCase());
        if (coins.length === 0) coins = data.filter(coin => coin.symbol.toLowerCase() === coinId.toLowerCase());
        if (coins.length === 0) throw new Error(`Cryptocurrency with name or symbol "${coinId}" not found.`);

        if (coins.length > 1) {
            const marketCapResponse = await fetch('https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc');
            if (!marketCapResponse.ok) throw new Error('Failed to fetch market cap data.');
            const marketCapData = await marketCapResponse.json();
            const popularCoin = coins.find(coin => marketCapData.some(mcCoin => mcCoin.id === coin.id));
            if (popularCoin) return popularCoin.id;
        }

        return coins[0].id;
    }

    function isValidContractAddress(address) {
        return /^0x[a-fA-F0-9]{40}$/.test(address);
    }

async function fetchData(coinId, contractAddress, network, epochs, batchSize, neurons, filters, kernelSize, forecastDays, modelArchitecture) {
    showLoading();

    const url = `/api.php?coinId=${coinId}&contractAddress=${contractAddress}&network=${network}`;
    console.log('Fetching data from:', url);

    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error('Network response was not ok.');
        const data = await response.json();
        console.log('Data from server:', data);

        if (data.error) {
            alert(data.error);
        } else {
            // Отображаем имя криптовалюты и прогноз
            document.getElementById('coinName').innerText = `Coin: ${data.coin_id}`;
            document.getElementById('forecastChange').innerText = `Forecast Change: ${data.forecast_change}%`;
            document.getElementById('result').style.display = 'block';

            // Рендерим график
            const forecastChange = await renderChartWithForecast(data.prices, epochs, batchSize, neurons, filters, kernelSize, forecastDays, modelArchitecture);
            await savePredictionToServer(coinId, contractAddress, network, forecastChange);
            console.log('Forecast change:', forecastChange);
            if (forecastChange > 0) console.log('The forecast predicts a gain.');
            else console.log('The forecast predicts a loss.');
        }
    } catch (error) {
        console.error('Error:', error);
        alert('Failed to fetch data. Please try again later.');
    } finally {
        hideLoading();
    }
}

    function prepareData(prices) {
        const historicalData = prices.map(price => price[1]);
        const maxPrice = Math.max(...historicalData);
        const normalizedData = historicalData.map(price => price / maxPrice);

        const X = [];
        const y = [];
        const sequenceLength = 10;

        for (let i = 0; i < normalizedData.length - sequenceLength; i++) {
            const sequence = normalizedData.slice(i, i + sequenceLength);
            X.push(sequence.map(value => [value]));
            y.push([normalizedData[i + sequenceLength]]);
        }

        return { X: tf.tensor3d(X), y: tf.tensor2d(y), maxPrice };
    }

    // Создание модели в зависимости от архитектуры
    async function createModel(modelArchitecture, neurons, filters, kernelSize) {
        switch (modelArchitecture) {
            case 'LSTM':
                return createLSTMModel(neurons);
            case 'CNN':
                return createCNNModel(filters, kernelSize);
            case 'GRU':
                return createGRUModel(neurons);
            case 'MLP':
                return createMLPModel();
            case 'Hybrid':
                return createHybridModel(filters, kernelSize, neurons);
            default:
                throw new Error('Unsupported model architecture');
        }
    }

    async function createLSTMModel(neurons) {
        const model = tf.sequential();
        model.add(tf.layers.lstm({ units: neurons, inputShape: [10, 1], returnSequences: false }));
        model.add(tf.layers.dense({ units: 1 }));
        model.compile({ optimizer: tf.train.adam(0.01), loss: 'meanSquaredError' });
        return model;
    }

    async function createCNNModel(filters, kernelSize) {
        const model = tf.sequential();
        model.add(tf.layers.conv1d({ filters, kernelSize, inputShape: [10, 1], activation: 'relu', padding: 'same' }));
        model.add(tf.layers.maxPooling1d({ poolSize: 2 }));
        model.add(tf.layers.flatten());
        model.add(tf.layers.dense({ units: 50, activation: 'relu' }));
        model.add(tf.layers.dense({ units: 1 }));
        model.compile({ optimizer: tf.train.adam(0.01), loss: 'meanSquaredError' });
        return model;
    }

    async function createGRUModel(neurons) {
        const model = tf.sequential();
        model.add(tf.layers.gru({ units: neurons, inputShape: [10, 1], returnSequences: false }));
        model.add(tf.layers.dense({ units: 1 }));
        model.compile({ optimizer: tf.train.adam(0.01), loss: 'meanSquaredError' });
        return model;
    }

    async function createMLPModel() {
        const model = tf.sequential();
        model.add(tf.layers.flatten({ inputShape: [10, 1] }));
        model.add(tf.layers.dense({ units: 50, activation: 'relu' }));
        model.add(tf.layers.dense({ units: 1 }));
        model.compile({ optimizer: tf.train.adam(0.01), loss: 'meanSquaredError' });
        return model;
    }

    async function createHybridModel(filters, kernelSize, neurons) {
        const model = tf.sequential();
        model.add(tf.layers.conv1d({ filters, kernelSize, inputShape: [10, 1], activation: 'relu', padding: 'same' }));
        model.add(tf.layers.maxPooling1d({ poolSize: 2 }));
        model.add(tf.layers.lstm({ units: neurons, returnSequences: false }));
        model.add(tf.layers.dense({ units: 1 }));
        model.compile({ optimizer: tf.train.adam(0.01), loss: 'meanSquaredError' });
        return model;
    }

    async function renderChartWithForecast(prices, epochs, batchSize, neurons, filters, kernelSize, forecastDays, modelArchitecture) {
        const ctx = document.getElementById('coinChart').getContext('2d');

        try {
            const labels = prices.map((price, index) => {
                const date = new Date();
                date.setDate(date.getDate() + index - prices.length + 1);
                return date.toLocaleDateString();
            });

            const data = prices.map(price => price[1]);
            const { X, y, maxPrice } = prepareData(prices);

            const model = await createModel(modelArchitecture, neurons, filters, kernelSize);
            await trainModel(model, X, y, epochs, batchSize);

            const forecastPrices = await predictWithModel(model, prices, maxPrice, forecastDays);
            const forecastLabels = [];
            for (let i = 0; i < forecastDays; i++) {
                const date = new Date();
                date.setDate(date.getDate() + i + 1);
                forecastLabels.push(date.toLocaleDateString());
            }

            if (window.myChart) window.myChart.destroy();

            window.myChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: [...labels, ...forecastLabels],
                    datasets: [
                        {
                            label: 'Historical Price (USD)',
                            data: [...data, ...Array(forecastDays).fill(null)],
                            borderColor: '#007bff',
                            backgroundColor: 'rgba(0, 123, 255, 0.1)',
                            fill: true,
                            tension: 0.4
                        },
                        {
                            label: 'Forecast Price (USD)',
                            data: [...Array(data.length).fill(null), ...forecastPrices],
                            borderColor: '#ff6384',
                            backgroundColor: 'rgba(255, 99, 132, 0.1)',
                            fill: true,
                            tension: 0.4
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        x: { display: true, title: { display: true, text: 'Date' } },
                        y: { display: true, title: { display: true, text: 'Price (USD)' } }
                    },
                    plugins: {
                        tooltip: { mode: 'index', intersect: false },
                        legend: { display: true, position: 'top' }
                    }
                }
            });

            const lastHistoricalPrice = data[data.length - 1];
            const lastForecastPrice = forecastPrices[forecastPrices.length - 1];
            const forecastChange = ((lastForecastPrice - lastHistoricalPrice) / lastHistoricalPrice) * 100;
            console.log('Forecast change:', forecastChange);
            return forecastChange;
        } catch (error) {
            console.error('Error in renderChartWithForecast:', error);
            alert('Failed to render chart. Please try again later.');
            return 0;
        }
    }

async function predictWithModel(model, prices, maxPrice, forecastDays) {
    const { X } = prepareData(prices);
    const predictions = [];
    const lastSequenceIndex = X.shape[0] - 1;
    let lastSequence = X.slice([lastSequenceIndex, 0, 0], [1, 10, 1]);

    // Рассчитываем волатильность исторических данных
    const historicalVolatility = calculateVolatility(prices);
    const noiseLevel = historicalVolatility > 0.1 ? 0.1 : historicalVolatility; // Максимум 10% шума

    for (let i = 0; i < forecastDays; i++) {
        const prediction = model.predict(lastSequence);
        const denormalizedPrediction = prediction.dataSync()[0] * maxPrice;

        // Добавляем шум к прогнозу
        const noise = (Math.random() * 2 - 1) * noiseLevel * denormalizedPrediction; // Шум в пределах ±noiseLevel%
        const noisyPrediction = denormalizedPrediction + noise;

        predictions.push(noisyPrediction);

        lastSequence = tf.concat([
            lastSequence.slice([0, 1, 0], [1, 9, 1]),
            prediction.reshape([1, 1, 1])
        ], 1);
    }

    return predictions;
}

// Функция для расчета волатильности исторических данных
function calculateVolatility(prices) {
    const returns = [];
    for (let i = 1; i < prices.length; i++) {
        const prevPrice = prices[i - 1][1];
        const currentPrice = prices[i][1];
        const dailyReturn = (currentPrice - prevPrice) / prevPrice;
        returns.push(dailyReturn);
    }

    const meanReturn = returns.reduce((sum, ret) => sum + ret, 0) / returns.length;
    const variance = returns.reduce((sum, ret) => sum + Math.pow(ret - meanReturn, 2), 0) / returns.length;
    const volatility = Math.sqrt(variance); // Стандартное отклонение как мера волатильности

    return Math.min(volatility, 0.1); // Ограничиваем волатильность 10%
}

    async function trainModel(model, X, y, epochs, batchSize) {
        console.log('Starting model training...');
        const totalEpochs = epochs;

        for (let epoch = 0; epoch < totalEpochs; epoch++) {
            const history = await model.fit(X, y, { epochs: 1, batchSize, verbose: 0 });
            const progress = ((epoch + 1) / totalEpochs) * 100;
            progressElement.textContent = `Progress: ${progress.toFixed(0)}%`;
            console.log(`Epoch ${epoch + 1}: loss = ${history.history.loss[0]}`);
            await tf.nextFrame();
        }
        console.log('Model training completed.');
    }

    async function savePredictionToServer(coinId, contractAddress, network, forecastChange) {
        try {
            const response = await fetch('save_prediction.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ coinId, contractAddress, network, forecastChange })
            });
            const data = await response.json();
            console.log('Prediction submitted:', data);
        } catch (error) {
            console.error('Error submitting prediction:', error);
        }
    }

    function showLoading() {
        loadingElement.style.display = 'block';
    }

    function hideLoading() {
        loadingElement.style.display = 'none';
    }
});