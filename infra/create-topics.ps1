# ============================================================
# Create Kafka Topics for Snapgram (PowerShell version)
# Usage:
#   .\infra\create-topics.ps1
#   .\infra\create-topics.ps1 -KafkaContainer "snapgram-kafka" -BootstrapServer "localhost:9092"
# ============================================================
param(
    [string]$KafkaContainer = "snapgram-kafka",
    [string]$BootstrapServer = "localhost:9092",
    [int]$MaxRetries = 30,
    [int]$RetryIntervalSec = 2
)

$ErrorActionPreference = "Stop"

$topics = @(
    @{ Name = "user.events"; Partitions = 3 },
    @{ Name = "content.post-created"; Partitions = 3 },
    @{ Name = "content.post-updated"; Partitions = 3 },
    @{ Name = "content.interaction"; Partitions = 3 },
    @{ Name = "content.story-created"; Partitions = 3 },
    @{ Name = "follow.events"; Partitions = 3 },
    @{ Name = "chat.messages"; Partitions = 6 },
    @{ Name = "chat.escalation"; Partitions = 3 },
    @{ Name = "chat.state-changed"; Partitions = 3 },
    @{ Name = "staff.presence"; Partitions = 3 },
    @{ Name = "call.events"; Partitions = 3 },
    @{ Name = "mention.events"; Partitions = 3 },
    @{ Name = "moderation.events"; Partitions = 3 },
    @{ Name = "notification.events"; Partitions = 3 },
    @{ Name = "media.uploaded"; Partitions = 3 }
)

Write-Host "Waiting for Kafka at $BootstrapServer inside container '$KafkaContainer' (max $MaxRetries retries)..." -ForegroundColor Cyan

$ready = $false
for ($i = 1; $i -le $MaxRetries; $i++) {
    $check = docker exec $KafkaContainer /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server $BootstrapServer 2>&1
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    Write-Host "   [$i/$MaxRetries] Kafka not ready yet, retrying in ${RetryIntervalSec}s..." -ForegroundColor Yellow
    Start-Sleep -Seconds $RetryIntervalSec
}

if (-not $ready) {
    Write-Error "Kafka not reachable after ($($MaxRetries * $RetryIntervalSec))s. Aborting."
    exit 1
}

Write-Host "Kafka is ready." -ForegroundColor Green
Write-Host ""
Write-Host "Creating topics..." -ForegroundColor Cyan

foreach ($t in $topics) {
    $topicName = $t.Name
    $partitions = $t.Partitions
    docker exec $KafkaContainer /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server $BootstrapServer `
        --create --if-not-exists `
        --topic $topicName `
        --partitions $partitions `
        --replication-factor 1 2>&1 | Out-Null
    Write-Host "   $topicName ($partitions partitions)" -ForegroundColor Green
}

Write-Host ""
Write-Host "All Kafka topics:" -ForegroundColor Cyan
docker exec $KafkaContainer /opt/kafka/bin/kafka-topics.sh --bootstrap-server $BootstrapServer --list | ForEach-Object {
    Write-Host "   $_"
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
