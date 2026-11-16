param([Parameter(Mandatory=$true)][string]$ServerIp)
Write-Host "== LTM Chat LAN diagnostic ==" -ForegroundColor Cyan
Write-Host "Server target: $ServerIp"
Test-NetConnection -ComputerName $ServerIp -Port 5000 -InformationLevel Detailed
Write-Host "UDP broadcast/multicast must be validated inside the app on both PCs." -ForegroundColor Yellow
