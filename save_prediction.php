<?php
session_start();
header('Content-Type: application/json');
require_once 'config1.php'; // Подключаем базу данных

// Получение данных из запроса
$input = json_decode(file_get_contents('php://input'), true);
if (!$input) {
    die(json_encode(['error' => 'Invalid input data']));
}

$coinId = $input['coinId'] ?? null;
$symbol = $input['symbol'] ?? null;
$contractAddress = $input['contractAddress'] ?? null;
$network = $input['network'] ?? null;
$forecastChange = $input['forecastChange'] ?? null;

if (!$coinId && !$contractAddress) {
    die(json_encode(['error' => 'Coin ID or Contract Address is required.']));
}

// Если указан контрактный адрес, но не указан Coin ID, ищем его
if ($contractAddress && !$coinId) {
    $coinData = findCoinDataByContractAddress($contractAddress, $network);
    if ($coinData) {
        $coinId = $coinData['id'];
        $symbol = $coinData['symbol'];
    } else {
        die(json_encode(['error' => 'Failed to find Coin ID for the given contract address.']));
    }
} elseif (!$symbol) {
    // Если символ не передан при запросе по coinId, нужно его также получить
    $symbol = getCoinSymbolById($coinId);
    if (!$symbol) {
        die(json_encode(['error' => 'Failed to find symbol for the given Coin ID.']));
    }
}

// Проверяем, существует ли запись в таблице predictions
$query = "SELECT * FROM predictions WHERE " . ($contractAddress ? "contract_address = ?" : "coin_id = ?");
$stmt = $pdo->prepare($query);
$stmt->execute([$contractAddress ?: $coinId]);
$existingRecord = $stmt->fetch();

if ($existingRecord) {
    // Обновляем запись в predictions
    $averageChange = ($existingRecord['average_forecast_change'] * $existingRecord['forecast_count'] + $forecastChange) / ($existingRecord['forecast_count'] + 1);
    $updateQuery = "UPDATE predictions SET forecast_change = ?, average_forecast_change = ?, forecast_count = forecast_count + 1, last_checked_at = NOW() WHERE id = ?";
    $updateStmt = $pdo->prepare($updateQuery);
    $updateStmt->execute([$forecastChange, $averageChange, $existingRecord['id']]);
} else {
    // Создаем новую запись в predictions
    $insertQuery = "INSERT INTO predictions (coin_id, symbol, contract_address, network, forecast_change, average_forecast_change, last_checked_at) 
                    VALUES (?, ?, ?, ?, ?, ?, NOW())";
    $insertStmt = $pdo->prepare($insertQuery);
    $insertStmt->execute([$coinId, $symbol, $contractAddress, $network, $forecastChange, $forecastChange]);
}

// Запись в таблицу crypto_history
if (isset($_SESSION['user_id'])) {
    $userId = $_SESSION['user_id'];
    if (empty($userId)) {
        die(json_encode(['error' => 'User ID is not set in session.']));
    }

    // Вставляем запись в crypto_history
    try {
        $historyInsertQuery = "INSERT INTO crypto_history (user_id, crypto_name, symbol, contract_address, network, percentage_change) 
                               VALUES (?, ?, ?, ?, ?, ?)";
        $historyStmt = $pdo->prepare($historyInsertQuery);
        $historyStmt->execute([$userId, $coinId, $symbol, $contractAddress, $network, $forecastChange]);
    } catch (PDOException $e) {
        die(json_encode(['error' => 'Failed to insert data into crypto_history: ' . $e->getMessage()]));
    }
} else {
    die(json_encode(['error' => 'User not logged in.']));
}

echo json_encode(['success' => true]);

// Функция для поиска Coin ID и символа по контрактному адресу
function findCoinDataByContractAddress($contractAddress, $network) {
    $platformId = mapNetworkToPlatformId($network);
    if (!$platformId) {
        return null;
    }

    $url = "https://api.coingecko.com/api/v3/coins/$platformId/contract/$contractAddress";
    $response = file_get_contents($url);
    
    if ($response === FALSE) {
        return null;  // Ошибка при обращении к API
    }

    $data = json_decode($response, true);
    
    // Проверяем, получены ли корректные данные
    if (isset($data['id'], $data['symbol'])) {
        return ['id' => $data['id'], 'symbol' => $data['symbol']];
    }
    return null;
}

// Функция для преобразования сети в platform_id (CoinGecko)
function mapNetworkToPlatformId($network) {
    $networkMap = [
        'ethereum' => 'ethereum',
        'binance-smart-chain' => 'binance-smart-chain',
        'polygon-pos' => 'polygon-pos',
        'avalanche' => 'avalanche',
        'fantom' => 'fantom',
        'arbitrum-one' => 'arbitrum-one',
        'optimistic-ethereum' => 'optimistic-ethereum',
    ];

    return $networkMap[strtolower($network)] ?? null;
}

// Функция для получения символа по Coin ID
function getCoinSymbolById($coinId) {
    $url = "https://api.coingecko.com/api/v3/coins/$coinId";
    $response = file_get_contents($url);
    
    if ($response === FALSE) {
        return null; // Ошибка при обращении к API
    }

    $data = json_decode($response, true);
    
    // Проверяем, получен ли символ
    return $data['symbol'] ?? null;
}
?>
