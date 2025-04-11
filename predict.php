<?php
require_once 'config.php';

if (isset($_POST['coinId']) || isset($_POST['contractAddress'])) {
    $coinId = $_POST['coinId'] ?? null;
    $contractAddress = $_POST['contractAddress'] ?? null;

    // Логируем полученные данные для отладки
    error_log("Received Coin ID: $coinId, Contract Address: $contractAddress");

    // Нормализуем данные
    $coinId = strtolower(trim($coinId));
    $contractAddress = strtolower(trim($contractAddress));

    if (empty($coinId) && empty($contractAddress)) {
        echo json_encode(['success' => false, 'message' => 'Coin ID or Contract Address is missing']);
        exit;
    }

    // Строим SQL-запрос с проверкой на оба параметра
    $sql = "SELECT coin_id, forecast_change, symbol FROM predictions WHERE ";
    $params = [];
    $queryParts = [];

    // Если передан coinId, ищем по обоим полям
    if ($coinId) {
        $queryParts[] = "(LOWER(coin_id) = ? OR LOWER(symbol) = ?)";  // Ищем по полям coin_id или symbol
        $params[] = $coinId;
        $params[] = $coinId;
    }

    // Если передан contractAddress, ищем по нему
    if ($contractAddress) {
        $queryParts[] = "LOWER(contract_address) = ?";
        $params[] = $contractAddress;
    }

    // Объединяем части запроса с учетом наличия одного или двух параметров
    $sql .= implode(" OR ", $queryParts);

    // Логируем финальный SQL запрос для отладки
    error_log("Final SQL query: $sql");

    // Подготавливаем и выполняем запрос
    $stmt = $conn->prepare($sql);
    if ($stmt === false) {
        error_log("SQL error: " . $conn->error);  // Логируем ошибку SQL
        die("SQL error: " . $conn->error);
    }

    // Привязываем параметры
    $stmt->bind_param(str_repeat("s", count($params)), ...$params);

    $stmt->execute();
    $result = $stmt->get_result();

    if ($result->num_rows > 0) {
        $row = $result->fetch_assoc();
        $coin_id = $row['coin_id']; // Имя криптовалюты
        $forecast_change = $row['forecast_change'];
        $symbol = $row['symbol'] ?? null; // Символ

        // Логируем результат для отладки
        error_log("Query result: " . json_encode($row));

        echo json_encode([
            'success' => true,
            'coin_id' => $coin_id,
            'forecast_change' => $forecast_change,
            'symbol' => $symbol
        ]);
    } else {
        echo json_encode(['success' => false, 'message' => 'Prediction not available']);
    }
} else {
    echo json_encode(['success' => false, 'message' => 'No valid data provided']);
}
?>
