Add-Type -AssemblyName System.Drawing

$outDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$items = @(
    @{ File='C:\Users\Bowei\AppData\Local\Temp\codex-clipboard-30027372-57a8-4f40-bb35-e7816772a920.png'; Name='01-one-live.png'; Eyebrow='THE GLOBAL SCREEN'; Title="THERE IS ONLY`nONE MESSAGE."; Bg='#D7FF00'; Fg='#070707'; Accent='#070707' },
    @{ File='C:\Users\Bowei\AppData\Local\Temp\codex-clipboard-91dd789d-6b02-4fbd-bd28-d8c9f4e1ab5a.png'; Name='02-stolen.png'; Eyebrow='LIVE RIVALRY'; Title="THEY TOOK IT.`nTAKE IT BACK."; Bg='#FF573B'; Fg='#080808'; Accent='#080808' },
    @{ File='C:\Users\Bowei\AppData\Local\Temp\codex-clipboard-d3c56e05-bf99-4e97-aee1-f0d4253eef72.png'; Name='03-words.png'; Eyebrow='YOUR MESSAGE LIBRARY'; Title="WORDS READY`nFOR THE WORLD."; Bg='#080808'; Fg='#F4F2EB'; Accent='#D7FF00' },
    @{ File='C:\Users\Bowei\AppData\Local\Temp\codex-clipboard-c100369e-3e4d-48b8-8c20-585780aa9336.png'; Name='04-takeover.png'; Eyebrow='RACE-SAFE TAKEOVERS'; Title="ONE SCREEN.`nONE WINNER."; Bg='#D7FF00'; Fg='#070707'; Accent='#070707' },
    @{ File='C:\Users\Bowei\AppData\Local\Temp\codex-clipboard-76a8a97f-2fd1-447c-b28c-b0e290e37675.png'; Name='05-proof.png'; Eyebrow='SERVER-VERIFIED'; Title="WIN IT.`nPROVE IT."; Bg='#080808'; Fg='#F4F2EB'; Accent='#D7FF00' },
    @{ File='C:\Users\Bowei\AppData\Local\Temp\codex-clipboard-8e8f3c7a-45f6-4622-8a00-a1e7eb53480f.png'; Name='06-hall.png'; Eyebrow='THE HALL OF ONE'; Title="FAME HAS A`nLEADERBOARD."; Bg='#D7FF00'; Fg='#070707'; Accent='#070707' }
)

function Color([string]$hex) { return [System.Drawing.ColorTranslator]::FromHtml($hex) }
function RoundedRect([float]$x,[float]$y,[float]$w,[float]$h,[float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x,$y,$d,$d,180,90); $p.AddArc($x+$w-$d,$y,$d,$d,270,90)
    $p.AddArc($x+$w-$d,$y+$h-$d,$d,$d,0,90); $p.AddArc($x,$y+$h-$d,$d,$d,90,90)
    $p.CloseFigure(); return $p
}

$index = 0
foreach ($item in $items) {
    $index++
    $canvas = New-Object System.Drawing.Bitmap 1080,1920
    $g = [System.Drawing.Graphics]::FromImage($canvas)
    $g.SmoothingMode = 'AntiAlias'; $g.InterpolationMode = 'HighQualityBicubic'; $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear((Color $item.Bg))

    # Subtle broadcast rings.
    $ringPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(28,(Color $item.Accent))),2
    foreach ($size in @(220,360,520,700)) { $g.DrawEllipse($ringPen,900-$size/2,160-$size/2,$size,$size) }
    $ringPen.Dispose()

    $small = New-Object System.Drawing.Font('Consolas',22,[System.Drawing.FontStyle]::Bold)
    $title = New-Object System.Drawing.Font('Arial Black',62,[System.Drawing.FontStyle]::Bold)
    $num = New-Object System.Drawing.Font('Arial Black',34,[System.Drawing.FontStyle]::Bold)
    $fgBrush = New-Object System.Drawing.SolidBrush (Color $item.Fg)
    $accentBrush = New-Object System.Drawing.SolidBrush (Color $item.Accent)
    $g.DrawString(('0'+$index),$num,$fgBrush,64,48)
    $g.DrawString($item.Eyebrow,$small,$fgBrush,162,66)
    $g.DrawString($item.Title,$title,$fgBrush,60,140)
    $g.FillRectangle($accentBrush,64,352,140,10)
    $g.DrawString('ONE  /  THE ONLY LIVE MESSAGE',$small,$fgBrush,225,375)

    # Phone shadow and body.
    $phoneX=183; $phoneY=430; $phoneW=714; $phoneH=1450
    $shadowPath = RoundedRect ($phoneX+18) ($phoneY+24) $phoneW $phoneH 58
    $shadowBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(80,0,0,0))
    $g.FillPath($shadowBrush,$shadowPath)
    $phonePath = RoundedRect $phoneX $phoneY $phoneW $phoneH 58
    $phoneBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255,12,12,14))
    $borderPen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(115,(Color $item.Accent))),4
    $g.FillPath($phoneBrush,$phonePath); $g.DrawPath($borderPen,$phonePath)

    $src = [System.Drawing.Image]::FromFile($item.File)
    $screenX=$phoneX+20; $screenY=$phoneY+20; $screenW=$phoneW-40; $screenH=$phoneH-40
    $screenPath = RoundedRect $screenX $screenY $screenW $screenH 42
    $oldClip=$g.Clip; $g.SetClip($screenPath)
    $scale=[Math]::Max($screenW/$src.Width,$screenH/$src.Height)
    $drawW=$src.Width*$scale; $drawH=$src.Height*$scale
    $drawX=$screenX+($screenW-$drawW)/2; $drawY=$screenY+($screenH-$drawH)/2
    $g.DrawImage($src,[float]$drawX,[float]$drawY,[float]$drawW,[float]$drawH)
    $g.Clip=$oldClip

    $dest=Join-Path $outDir $item.Name
    $canvas.Save($dest,[System.Drawing.Imaging.ImageFormat]::Png)
    $src.Dispose(); $screenPath.Dispose(); $borderPen.Dispose(); $phoneBrush.Dispose(); $phonePath.Dispose()
    $shadowBrush.Dispose(); $shadowPath.Dispose(); $fgBrush.Dispose(); $accentBrush.Dispose(); $small.Dispose(); $title.Dispose(); $num.Dispose(); $g.Dispose(); $canvas.Dispose()
}

Get-ChildItem $outDir -Filter '0*.png' | Select-Object Name,Length
