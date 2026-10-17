# Clipboard item texture and GUI (original art). GUI layout must match ClipboardScreen.
param([Parameter(Mandatory = $true)][string]$Tex)
. "$PSScriptRoot\ArtLib.ps1"

$woodHL = Hex 'C99D66'; $woodL = Hex 'B08550'; $woodM = Hex '946B3E'; $woodD = Hex '74512D'; $woodDD = Hex '4F361D'
$papHL = Hex 'FBF3DD'; $papL = Hex 'F2E8CC'; $papM = Hex 'E2D4AE'; $papD = Hex 'BFAE84'
$steelHL = Hex 'D5D8DD'; $steelL = Hex 'B0B4BB'; $steelM = Hex '8A8E95'; $steelD = Hex '5E6168'
$rule = Hex 'C9D3DE'; $margin = Hex 'D98C8C'

# ---- item (16x16)
$t = NewImg 16 16
Grid $t 0 0 @(
    '................',
    '.....ssss.......',
    '..wwwsSSswww....',
    '..wWWsssswWw....',
    '..wWpppppppw....',
    '..wWpkkkkppw....',
    '..wWpppppppw....',
    '..wWpkkkkkpw....',
    '..wWpppppppw....',
    '..wWpkkkppppw...',
    '..wWpppppppw....',
    '..wWpkkkkppw....',
    '..wWpppppppw....',
    '..wwwwwwwwww....',
    '................',
    '................') (Pal 'w' $woodD 'W' $woodM 'p' $papL 'k' (Hex '8E9AA8') 's' $steelM 'S' $steelHL)
AutoOutline $t 0 0 16 16 $woodDD
Save $t "$Tex\item\clipboard.png"

# ---- GUI (256x256): board 176x220 at 0,0; checkbox sprites at (180,0) empty, (190,0) checked, (200,0) faint
$g = NewImg 256 256
$W = 176; $H = 220
# wooden board with rounded corners
for ($x = 0; $x -lt $W; $x++) { for ($y = 0; $y -lt $H; $y++) {
    $corner = ($x -lt 3 -and $y -lt 3 -and ($x + $y) -lt 3) -or ($x -gt $W - 4 -and $y -lt 3 -and ($W - 1 - $x + $y) -lt 3) -or
        ($x -lt 3 -and $y -gt $H - 4 -and ($x + $H - 1 - $y) -lt 3) -or ($x -gt $W - 4 -and $y -gt $H - 4 -and ($W - 1 - $x + $H - 1 - $y) -lt 3)
    if (-not $corner) { Px $g $x $y (Shade $woodM ($script:Rng.Next(-5, 6))) }
} }
for ($y = 4; $y -lt $H; $y += 7) { Fill $g 3 $y ($W - 4) $y (Shade $woodM -10) }   # grain
Bevel $g 1 1 ($W - 2) ($H - 2) $woodHL $woodDD
Bevel $g 2 2 ($W - 3) ($H - 3) $woodL $woodD
# paper sheet
Fill $g 12 22 163 214 $papL
for ($y = 23; $y -le 213; $y++) { Px $g 163 $y $papD; Px $g 162 $y $papM }
Fill $g 12 214 163 214 $papD
Fill $g 12 22 162 22 $papHL
for ($i = 0; $i -lt 10; $i++) { $ry = 36 + $i * 16 + 15; Fill $g 14 $ry 160 $ry $rule }
Fill $g 31 24 31 196 $margin
# metal clip
Fill $g 58 4 117 26 $steelM
Bevel $g 58 4 117 26 $steelHL $steelD
Fill $g 62 8 113 12 $steelL
Fill $g 78 0 97 6 $steelM; Bevel $g 78 0 97 6 $steelHL $steelD; Fill $g 84 2 91 3 $steelD
# checkboxes
foreach ($cx in 180, 190, 200) {
    $border = if ($cx -eq 200) { Hex 'C8BFA8' } else { Hex '6B5A42' }
    Fill $g $cx 0 ($cx + 9) 9 $papHL
    Bevel $g $cx 0 ($cx + 9) 9 $border $border
}
Grid $g 191 1 @(
    '.......G',
    '......GG',
    'G....GG.',
    'GG..GG..',
    '.GGGG...',
    '..GG....',
    '........') (Pal 'G' (Hex '2E8B3A'))
Save $g "$Tex\gui\clipboard.png"
Write-Host "clipboard art written"
