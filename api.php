<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *'); // Разрешаем запросы с любого домена
header('Access-Control-Allow-Methods: GET');

if (isset($_GET['coinId']) || isset($_GET['contractAddress'])) {
    $coinId = $_GET['coinId'] ?? null;
    $contractAddress = $_GET['contractAddress'] ?? null;
    $network = $_GET['network'] ?? 'ethereum'; // По умолчанию Ethereum
    $days = $_GET['days'] ?? 365; // По умолчанию 365 дней

    // Ограничиваем максимальное количество дней до 365
    $days = min($days, 365);

    // Проверка на одновременное указание coinId и contractAddress
    if ($coinId && $contractAddress) {
        echo json_encode(['error' => 'Please enter either Coin ID or Contract Address, not both.']);
        exit;
    }

    // Если указан адрес контракта, используем его
    if ($contractAddress) {
        // Проверка формата адреса контракта
        if (!preg_match('/^0x[a-fA-F0-9]{40}$/', $contractAddress)) {
            echo json_encode(['error' => 'Invalid contract address.']);
            exit;
        }

        // Формируем URL для запроса данных по адресу контракта
        $url = "https://api.coingecko.com/api/v3/coins/{$network}/contract/{$contractAddress}/market_chart?vs_currency=usd&days={$days}";
    } else {
        // Используем coinId (например, bitcoin, ethereum, ripple)
        $coinId = strtolower($coinId); // Преобразуем в нижний регистр

        // Проверка на пустой coinId
        if (empty($coinId)) {
            echo json_encode(['error' => 'Please enter a valid Coin ID.']);
            exit;
        }

        // Формируем URL для запроса данных по coinId
        $url = "https://api.coingecko.com/api/v3/coins/{$coinId}/market_chart?vs_currency=usd&days={$days}";
    }

    // Логирование для отладки
    error_log("Request URL: " . $url);

    // Выполняем запрос к CoinGecko API
    $ch = curl_init();
    curl_setopt($ch, CURLOPT_URL, $url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);

    // Логирование ответа
    error_log("HTTP Code: " . $httpCode);
    error_log("Response: " . $response);

    // Обработка ошибок
    if ($httpCode !== 200) {
        echo json_encode([
            'error' => 'Failed to fetch data from CoinGecko API.',
            'http_code' => $httpCode,
            'response' => $response
        ]);
        exit;
    }

    // Проверка на пустой ответ
    if (empty($response)) {
        echo json_encode(['error' => 'Empty response from CoinGecko API.']);
        exit;
    }

    $data = json_decode($response, true);
    if (json_last_error() !== JSON_ERROR_NONE) {
        echo json_encode(['error' => 'Invalid JSON response from CoinGecko API.']);
        exit;
    }

    // Проверка наличия данных
    if (!isset($data['prices'])) {
        echo json_encode(['error' => 'Invalid data format from CoinGecko API.']);
        exit;
    }

    // Возвращаем данные
    echo json_encode($data);
} else {
    echo json_encode(['error' => 'Please enter either Coin ID or Contract Address.']);
}
?>