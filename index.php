<?php
session_start();
require_once 'config.php'; // Подключаем конфигурационный файл

// Проверяем, вошел ли пользователь в систему
$is_premium = false;
$is_admin = false; // По умолчанию пользователь не админ
$premium_time_left = null; // По умолчанию оставшееся время не установлено
if (isset($_SESSION['username'])) {
    $username = $_SESSION['username'];
    
    // Используем подготовленные выражения для безопасности
    $sql = "SELECT has_premium, is_admin, premium_expiry FROM users WHERE username = ?";
    $stmt = $conn->prepare($sql);
    
    if (!$stmt) {
        die("SQL Error: " . $conn->error);  // Печатаем ошибку, если запрос не подготовился
    }

    $stmt->bind_param("s", $username);
    $stmt->execute();
    $result = $stmt->get_result();

    if ($result->num_rows > 0) {
        $row = $result->fetch_assoc();
        $is_premium = (bool)$row['has_premium']; // Приводим к булевому типу
        $is_admin = (bool)$row['is_admin']; // Проверяем, является ли пользователь админом
        $premium_expiry = $row['premium_expiry']; // Получаем время окончания премиума

        // Рассчитываем оставшееся время, если премиум активен
        if ($is_premium && $premium_expiry) {
            $current_time = new DateTime(); // Текущее время
            $expiry_time = new DateTime($premium_expiry); // Время окончания премиума
            $interval = $current_time->diff($expiry_time); // Разница между временами
            $premium_time_left = $interval->format('%a D %h H %i M'); // Форматируем разницу
        }
    } else {
        echo "No user found in the database.";  // Печатаем ошибку, если пользователь не найден
    }

    $stmt->close();
}
?>

<!DOCTYPE html>
<html lang="<?= $current_language ?>">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><?= $lang['title'] ?></title>
    <link rel="stylesheet" href="style.css">
    <link rel="icon" sizes="120x120" href="/logo.png">
    <link rel="icon" sizes="32x32" href="/logo.png">
    <link rel="icon" sizes="16x16" href="/logo.png">
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@3.0.0/dist/tf.min.js"></script>
    <script src="script.js" defer></script>
    <script src="https://telegram.org/js/telegram-web-app.js"></script>

    <style>
        input[readonly] {
            background-color: #f0f0f0;
            cursor: not-allowed;
        }
        @keyframes blink {
            0% { color: red; }
            25% { color: blue; }
            50% { color: green; }
            75% { color: yellow; }
            100% { color: purple; }
        }

        #blinking-text {
            animation: blink 2s infinite;
        }

        h1 span {
            font-size: 0.6em;
            color: green;
            margin-left: 10px;
        }
    </style>
</head>
<body>
    <div class="container">
<div class="scroll-container">
    <div class="buttons">
        <?php if (isset($_SESSION['username'])): ?>
                    <a href='logout.php' class='button'><?= $lang['logout'] ?></a>
            <?php if (!$is_premium): ?>
                        <a href='buy.php' class='button'><?= $lang['premium'] ?></a>
            <?php endif; ?>
            <?php if ($is_admin): ?>
                        <a href='admin.php' class='button'><?= $lang['admin_panel'] ?></a>
            <?php endif; ?>
        <?php else: ?>
                    <a href='login.php' class='button'><?= $lang['login'] ?></a>
                    <a href='register.php' class='button'><?= $lang['register'] ?></a>
        <?php endif; ?>
                <a href="cpp.php" class="button cpp-button">CPP</a>
                <a href="exchange.php" class="button"><?= $lang['birja'] ?></a>
        <!-- Изменяем кнопку About на Account для авторизованных пользователей -->
        <?php if (isset($_SESSION['username'])): ?>
                    <a href="account.php" class="button"><?= $lang['account'] ?></a>
        <?php else: ?>
                    <a href="about.php" class="button"><?= $lang['about'] ?></a>
        <?php endif; ?>
                <a href="feedback.php" class="button"><?= $lang['feedback'] ?></a>
    <div class="language-selector">
        <form action="set_language.php" method="post">
            <select name="language" onchange="this.form.submit()">
                <?php foreach ($supported_languages as $code => $name): ?>
                    <option value="<?= $code ?>" <?= $code == $current_language ? 'selected' : '' ?>>
                        <?= $name ?>
                    </option>
                <?php endforeach; ?>
            </select>
        </form>
    </div>
    </div>
</div>
        <h1><?= $lang['title'] ?></h1>
        <form id="coinForm">
            <label for="coinId">  
                <?php if ($is_premium && $premium_time_left): ?>
                    <h3><span style="font-size: 0.6em; color: green;">
                        (<?= sprintf($lang['premium_time_left'], $premium_time_left) ?>)
                    </span></h3>
            <?php endif; ?>
<?= $lang['coin_id'] ?></label>
            <input type="text" id="coinId" name="coinId" placeholder="<?= $lang['coin_id_placeholder'] ?>">
            <label for="contractAddress"><?= $lang['contract_address'] ?></label>
<input type="text" id="contractAddress" name="contractAddress" placeholder="<?= $lang['contract_address_placeholder'] ?>">
            <label for="network"><?= $lang['network'] ?></label>
<select id="network" name="network">
    <option value=""></option>
    <option value="ethereum"><?= $lang['ethereum'] ?></option>
    <option value="binance-smart-chain"><?= $lang['binance_smart_chain'] ?></option>
    <option value="polygon-pos"><?= $lang['polygon_pos'] ?></option>
    <option value="avalanche"><?= $lang['avalanche'] ?></option>
    <option value="fantom"><?= $lang['fantom'] ?></option>
    <option value="arbitrum-one"><?= $lang['arbitrum_one'] ?></option>
    <option value="optimistic-ethereum"><?= $lang['optimistic_ethereum'] ?></option>
</select>

            <!-- Блок настроек для всех авторизованных пользователей -->
            <?php if (isset($_SESSION['username'])): ?>
                    <button type="button" id="toggleTrainingSettings"><?= $lang['training_settings'] ?></button>
               <div id="trainingSettings" style="display: none;">
    <!-- Поле для выбора нейронной сети -->
<label for="modelArchitecture"><?= $lang['neural_network'] ?></label>
        <select id="modelArchitecture" name="modelArchitecture">
            <option value="LSTM" selected><?= $lang['lstm'] ?></option>
            <option value="CNN"><?= $lang['cnn'] ?></option>
            <option value="GRU"><?= $lang['gru'] ?></option>
            <option value="MLP"><?= $lang['mlp'] ?></option>
            <option value="Hybrid"><?= $lang['hybrid'] ?></option>
        </select>

            <label for="epochs"><?= $lang['epochs'] ?></label>
    <input type="number" id="epochs" name="epochs" value="20" min="1" max="1000" <?php if (!$is_premium) echo 'readonly'; ?>>
    <label for="batchSize"><?= $lang['batch_size'] ?></label>
    <input type="number" id="batchSize" name="batchSize" value="64" min="1" max="1024" <?php if (!$is_premium) echo 'readonly'; ?>>
                    <label for="neurons"><?= $lang['neurons'] ?></label>
                    <input type="number" id="neurons" name="neurons" value="20" min="1" max="1000" <?php if (!$is_premium) echo 'readonly'; ?>>
                    <!-- Добавляем поле для выбора количества дней -->
                    <label for="forecastDays"><?= $lang['forecast_days'] ?></label>
                    <input type="number" id="forecastDays" name="forecastDays" value="30" min="1" max="365" <?php if (!$is_premium) echo 'readonly'; ?>>
    <?php if ($is_premium): ?>
        <button type="button" id="closeTrainingSettings"><?= $lang['ok'] ?></button>
    <?php endif; ?>
</div>
<!-- Добавляем JavaScript -->
<script>
    document.addEventListener('DOMContentLoaded', function () {
        const modelArchitectureSelect = document.getElementById('modelArchitecture');
        const isPremium = <?php echo $is_premium ? 'true' : 'false'; ?>;

        if (!isPremium) {
            // Блокируем выбор других элементов, кроме первого
            modelArchitectureSelect.addEventListener('change', function () {
                if (modelArchitectureSelect.selectedIndex !== 0) {
                    modelArchitectureSelect.selectedIndex = 0; // Сбрасываем на первый элемент
                }
            });

            // Делаем остальные элементы визуально недоступными
            Array.from(modelArchitectureSelect.options).forEach((option, index) => {
                if (index !== 0) {
                    option.disabled = true; // Отключаем выбор других элементов
                }
            });
        }
    });
</script>
            <?php endif; ?>

            <!-- Подсказка для неавторизованных и непремиум-пользователей -->
            <?php if ((!$is_premium || !isset($_SESSION['username'])) && isset($_SESSION['username'])): ?>
                <p class="hint"><?= $lang['premium_hint'] ?></p>
            <?php endif; ?>

            <button type="submit" id="getDataButton"><?= $lang['get_data'] ?></button>
            <p class="hint"><?= $lang['hint'] ?></p>
        </form>

        <div id="loading" style="display: none;">
    <div><?= $lang['loading'] ?></div>
    <div id="progress"><?= sprintf($lang['progress'], '0') ?></div>
        </div>
<div id="result" style="display: none;">
    <h2 id="coinName"></h2>
    <h3 id="forecastChange"></h3>
</div>
<script>
document.getElementById('getDataButton').addEventListener('click', function() {
    const coinId = document.getElementById('coinId').value;
    const contractAddress = document.getElementById('contractAddress').value;

    if (!coinId && !contractAddress) {
        alert('<?= $lang['alert_enter_coin_or_address'] ?>');
        return;
    }

    console.log(`Sending Coin ID: ${coinId}, Contract Address: ${contractAddress}`);  // Логирование данных

    // Инициализируем прогресс
    let progress = 0;
    const progressElement = document.getElementById('progress');
    const loadingDiv = document.getElementById('loading');
    
    loadingDiv.style.display = 'block';
    loadingDiv.querySelector('div').textContent = '<?= $lang['loading'] ?>';
    
    const interval = setInterval(function() {
        progress += 5;
        progressElement.innerHTML = '<?= sprintf($lang['progress'], "' + progress + '") ?>';
        
        if (progress >= 100) {
            clearInterval(interval);
            fetch('predict.php', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: 'coinId=' + encodeURIComponent(coinId) + '&contractAddress=' + encodeURIComponent(contractAddress)
            })
            .then(response => response.json())
.then(data => {
    loadingDiv.style.display = 'none';
    if (data.success) {
        document.getElementById('coinName').innerText = data.coin_id;
        document.getElementById('forecastChange').innerText = 
            '<?= $lang['forecast_change'] ?>: ' + data.forecast_change + '%';
        document.getElementById('result').style.display = 'block';
        
        // Отправляем запрос для начисления вознаграждения
        fetch('add_reward.php', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: 'username=<?= isset($_SESSION['username']) ? $_SESSION['username'] : "" ?>'
        })
        .then(response => response.json())
        .then(rewardData => {
            if (rewardData.success && rewardData.new_balance) {
                // Можно показать уведомление о начислении
                console.log('Начислено CPP: ' + rewardData.reward_amount);
            }
        });
    } else {
        document.getElementById('result').innerHTML = 
            '<h2><?= $lang['prediction_not_available'] ?></h2>';
        document.getElementById('result').style.display = 'block';
    }
});
        }
    }, 900);
});
</script>
        <div class="chart-container">
            <canvas id="coinChart"></canvas>
        </div>

<?php if (!$is_premium && isset($_SESSION['username'])): ?>
        <p class="hint"><?= $lang['view_tops'] ?></p>
<?php endif; ?>

<?php if ($is_premium): ?>
    <div class="buttons">
        <a href="gainers.php" class="button"><?= $lang['top_gainers'] ?></a>
        <a href="losers.php" class="button"><?= $lang['top_losers'] ?></a>
    </div>
<?php endif; ?>
<div class="buttons">
    <a href="https://t.me/Cryptoppru_bot" target="_blank"><?= $lang['telegram_bot'] ?></a>
</div>
    </div>

    <script>
        document.getElementById('getDataButton').addEventListener('click', function(event) {
            <?php if (!isset($_SESSION['username'])): ?>
                event.preventDefault(); // Останавливаем отправку формы
                window.location.href = 'register.php'; // Перенаправляем на страницу входа
            <?php endif; ?>
        });
    </script>
</body>
</html>
<?php
include 'foot.php';
?>