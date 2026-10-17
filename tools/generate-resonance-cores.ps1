# Original 16x16 iron-framed lapis cores. Deterministic pixel art, no external artwork.
Add-Type -AssemblyName System.Drawing
$shape=@(
'................',
'......ssss......',
'....sshhhhss....',
'...shhllllhhss..',
'..shllbbbbllhs..',
'..shlbbdddbbhs..',
'.shlbbddddblhs..',
'.shlbdddddbllhs.',
'.shlbdddddbllhs.',
'..shlbdddbblhs..',
'..shllbbbbllhs..',
'..sshhllllhhs...',
'....sshhhhss....',
'......ssss......',
'................',
'................')
foreach($attuned in @($false,$true)) {
    $palette=@{s='FF343D4A';h='FFE7E3D6';l='FF858F9F';b='FF203C80';d='FF406BB1'}
    $name='resonance_core'
    if($attuned) {$name='attuned_resonance_core';$palette=@{s='FF55412E';h='FFFFF0A3';l='FFCDAC5F';b='FF246B7A';d='FF83E7DC'}}
    $bitmap=[Drawing.Bitmap]::new(16,16)
    for($y=0;$y -lt 16;$y++) {for($x=0;$x -lt 16;$x++) {
        $symbol=$shape[$y][$x].ToString()
        if($palette.ContainsKey($symbol)) {$bitmap.SetPixel($x,$y,[Drawing.Color]::FromArgb([Convert]::ToInt32($palette[$symbol],16)))}
    }}
    $rune=if($attuned){[Drawing.Color]::FromArgb(255,239,255,219)}else{[Drawing.Color]::FromArgb(255,126,160,197)}
    foreach($pixel in @(@(7,5),@(8,5),@(6,6),@(9,6),@(7,7),@(8,7),@(7,8),@(8,8),@(6,9),@(9,9))) {$bitmap.SetPixel($pixel[0],$pixel[1],$rune)}
    $bitmap.Save((Join-Path $PSScriptRoot "../src/main/resources/assets/better_enchant/textures/item/$name.png"),[Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
    Set-Content -LiteralPath (Join-Path $PSScriptRoot "../src/main/resources/assets/better_enchant/items/$name.json") -Encoding utf8 -Value ('{"model":{"type":"minecraft:model","model":"better_enchant:item/'+$name+'"}}')
    Set-Content -LiteralPath (Join-Path $PSScriptRoot "../src/main/resources/assets/better_enchant/models/item/$name.json") -Encoding utf8 -Value ('{"parent":"minecraft:item/generated","textures":{"layer0":"better_enchant:item/'+$name+'"}}')
}
