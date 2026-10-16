# Original 16x16 provisional item sprite; no external assets.
Add-Type -AssemblyName System.Drawing
$bitmap=[Drawing.Bitmap]::new(16,16)
$shape=@('................','.....ssssss.....','...sslllllls....','..sllllllllls...','..sllmmmmmmmls..','.sllmmmmmmmmmls.','.slmmmmmmmmmmms.','.slmmmmmmmmmmms.','.slmmmmmmmmmmms.','.slmmmmmmmmmmms.','.slmmmmmmmmmmms.','..smmmmmmmmmmss.','..ssmmmmmmmmss..','...ssssssssss...','.....ssssss.....','................')
$palette=@{s='FF514D46';l='FFD1C8AD';m='FFA49D8A'}
for($y=0;$y -lt 16;$y++) {for($x=0;$x -lt 16;$x++) {$symbol=$shape[$y][$x].ToString();if($palette.ContainsKey($symbol)) {$bitmap.SetPixel($x,$y,[Drawing.Color]::FromArgb([Convert]::ToInt32($palette[$symbol],16)))}}}
foreach($pixel in @(@(6,4),@(7,4),@(8,4),@(9,4),@(6,5),@(9,5),@(6,6),@(8,6),@(6,7),@(7,7),@(6,8),@(8,8),@(6,9),@(9,9),@(6,10),@(9,10))) {$bitmap.SetPixel($pixel[0],$pixel[1],[Drawing.Color]::FromArgb(255,67,84,108))}
$bitmap.Save((Join-Path $PSScriptRoot '../src/main/resources/assets/better_enchant/textures/item/runic_stone.png'),[Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()
