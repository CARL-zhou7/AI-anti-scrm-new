$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path "$PSScriptRoot/../..").Path
$python = "$projectRoot/.tools/tts-env/Scripts/python.exe"
$cosyVoice = "$projectRoot/.tools/CosyVoice"
$localConfig = "$projectRoot/.tools/tts-local"
$model = "$cosyVoice/pretrained_models/CosyVoice2-0.5B"
foreach ($file in @($python, "$localConfig/voices.json", "$localConfig/bin/ffmpeg.exe", "$model/llm.pt", "$model/flow.pt", "$model/hift.pt", "$model/speech_tokenizer_v2.onnx")) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Local TTS installation is incomplete: $file" }
}
$previousPath = $env:PATH
$previousPythonPath = $env:PYTHONPATH
$previousModelScopeCache = $env:MODELSCOPE_CACHE
$previousHfHome = $env:HF_HOME
$previousMplConfig = $env:MPLCONFIGDIR
$previousModelScopeDomain = $env:MODELSCOPE_DOMAIN
$previousTemp = $env:TEMP
$previousTmp = $env:TMP
try {
    $env:PATH = "$localConfig/bin;$env:PATH"
    $env:PYTHONPATH = "$cosyVoice;$cosyVoice/third_party/Matcha-TTS"
    $env:MODELSCOPE_CACHE = "$localConfig/modelscope-cache"
    $env:HF_HOME = "$localConfig/huggingface-cache"
    $env:MPLCONFIGDIR = "$localConfig/matplotlib-cache"
    $env:MODELSCOPE_DOMAIN = 'modelscope.cn'
    New-Item -ItemType Directory -Force -Path "$localConfig/tmp" | Out-Null
    $env:TEMP = "$localConfig/tmp"
    $env:TMP = $env:TEMP
    & $python -u "$PSScriptRoot/app.py" --host 127.0.0.1 --port 50080 --voices "$localConfig/voices.json" --model-dir $model
    if ($LASTEXITCODE -ne 0) { throw "TTS gateway exited with code $LASTEXITCODE" }
} finally {
    $env:PATH = $previousPath
    $env:PYTHONPATH = $previousPythonPath
    $env:MODELSCOPE_CACHE = $previousModelScopeCache
    $env:HF_HOME = $previousHfHome
    $env:MPLCONFIGDIR = $previousMplConfig
    $env:MODELSCOPE_DOMAIN = $previousModelScopeDomain
    $env:TEMP = $previousTemp
    $env:TMP = $previousTmp
}
