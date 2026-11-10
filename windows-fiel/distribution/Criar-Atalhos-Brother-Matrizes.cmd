@echo off
setlocal
rem Brother Matrizes Windows - atalhos opcionais para o usuario atual.
rem Nao instala, nao usa administrador, nao altera registro ou associa arquivos.
rem Execute SOMENTE depois de extrair toda a pasta do ZIP QA.
set "BM_PORTABLE_FOLDER=%~dp0"
powershell.exe -NoLogo -NoProfile -Command "try { $ErrorActionPreference='Stop'; $root=$env:BM_PORTABLE_FOLDER; $exe=Join-Path $root 'BrotherMatrizes.exe'; if (!(Test-Path -LiteralPath $exe -PathType Leaf)) { throw 'BrotherMatrizes.exe nao encontrado. Extraia todo o ZIP primeiro.' }; foreach ($name in @('app','runtime','LEIA-ME-QA.txt')) { if (!(Test-Path -LiteralPath (Join-Path $root $name))) { throw ('Pacote portatil incompleto: '+$name) } }; $shell=New-Object -ComObject WScript.Shell; $places=@([Environment]::GetFolderPath('DesktopDirectory'),[Environment]::GetFolderPath('Programs')); foreach ($folder in $places) { if ([string]::IsNullOrWhiteSpace($folder) -or !(Test-Path -LiteralPath $folder -PathType Container)) { throw 'Pasta de atalhos do usuario indisponivel.' }; $link=Join-Path $folder 'Brother Matrizes.lnk'; if (Test-Path -LiteralPath $link) { $old=$shell.CreateShortcut($link); if (![string]::Equals([IO.Path]::GetFullPath($old.TargetPath),[IO.Path]::GetFullPath($exe),[StringComparison]::OrdinalIgnoreCase)) { throw ('Atalho existente preservado: '+$link) } } }; foreach ($folder in $places) { $link=Join-Path $folder 'Brother Matrizes.lnk'; $shortcut=$shell.CreateShortcut($link); $shortcut.TargetPath=$exe; $shortcut.WorkingDirectory=$root; $shortcut.IconLocation=$exe+',0'; $shortcut.Description='Brother Matrizes - Windows QA interna'; $shortcut.Save(); Write-Host ('Atalho criado: '+$link) }; exit 0 } catch { Write-Error $_.Exception.Message; exit 1 }"
if errorlevel 1 (
  echo ERRO: nenhum atalho existente de outro aplicativo sera substituido.
  exit /b 1
)
echo Atalhos Brother Matrizes criados para o usuario atual.
echo Mantenha a pasta do aplicativo no mesmo local. Se mover, execute este arquivo novamente.
exit /b 0
