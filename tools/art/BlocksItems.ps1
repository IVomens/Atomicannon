# Block and item textures (16x16). Original pixel art in a brass / steel / andesite palette.
param([Parameter(Mandatory = $true)][string]$Tex)
. "$PSScriptRoot\ArtLib.ps1"

$brassHL = Hex 'F7DE8A'; $brassL = Hex 'E6BC55'; $brassM = Hex 'C4953A'; $brassD = Hex '8C6224'; $brassDD = Hex '5A3D14'
$steelHL = Hex 'B4B8BF'; $steelL = Hex '979BA2'; $steelM = Hex '7A7E85'; $steelD = Hex '5B5E64'; $steelDD = Hex '35373B'
$andes = Hex '8F8E89'
$ironHL = Hex '8C9098'; $ironL = Hex '6D7179'; $ironM = Hex '54575E'; $ironD = Hex '3C3E43'; $ironDD = Hex '242528'
$woodHL = Hex 'C99D66'; $woodL = Hex 'B08550'; $woodM = Hex '946B3E'; $woodD = Hex '74512D'; $woodDD = Hex '4F361D'
$bpL = Hex '78ABEA'; $bpM = Hex '3F77C4'; $bpD = Hex '2C5A9E'; $bpDD = Hex '1D3D72'; $bpLine = Hex 'D8E9FF'
$papHL = Hex 'FBF3DD'; $papL = Hex 'EFE2C0'; $papM = Hex 'D9C79B'; $papD = Hex 'B39F70'; $papDD = Hex '6E5A36'

function Rivet($img, [int]$x, [int]$y) { Px $img $x $y $brassHL; Px $img ($x + 1) $y $brassM; Px $img $x ($y + 1) $brassM; Px $img ($x + 1) ($y + 1) $brassDD }

# ---- casing: steel frame with rivets around an andesite panel
function Casing([string]$name, [bool]$plate) {
    $t = NewImg 16 16
    Fill $t 0 0 15 15 $steelM
    Outline $t 0 0 15 15 $steelDD
    Bevel $t 1 1 14 14 $steelHL $steelD
    Bevel $t 3 3 12 12 $steelDD $steelL
    NoiseFill $t 4 4 11 11 $andes 6
    foreach ($p in @(@(5, 6), @(9, 5), @(7, 9), @(10, 10), @(4, 10))) { Px $t $p[0] $p[1] (Shade $andes -22) }
    foreach ($p in @(@(6, 4), @(10, 8), @(5, 9))) { Px $t $p[0] $p[1] (Shade $andes 16) }
    if ($plate) { Fill $t 4 7 11 8 $brassM; Fill $t 4 7 11 7 $brassL; Fill $t 4 8 11 8 $brassD }
    Rivet $t 1 1; Rivet $t 13 1; Rivet $t 1 13; Rivet $t 13 13
    Save $t "$Tex\block\$name.png"
}
Casing 'schematicannon_casing' $false
Casing 'schematicannon_casing_plate' $true

# ---- brass ring / trim
$t = NewImg 16 16
Fill $t 0 0 15 15 $brassM
Fill $t 0 0 15 0 $brassHL; Fill $t 0 1 15 1 $brassL
Fill $t 0 14 15 14 $brassD; Fill $t 0 15 15 15 $brassDD
for ($x = 1; $x -lt 16; $x += 4) { Px $t $x 7 $brassDD; Px $t $x 6 $brassL; Px $t ($x + 1) 8 $brassD }
Save $t "$Tex\block\schematicannon_brass.png"

# ---- iron turret plate
$t = NewImg 16 16
NoiseFill $t 0 0 15 15 $ironM 4
Outline $t 0 0 15 15 $ironDD
Bevel $t 1 1 14 14 $ironHL $ironD
Fill $t 7 2 8 13 $ironD; Fill $t 7 2 7 13 $ironL
Px $t 3 3 $ironHL; Px $t 12 3 $ironHL; Px $t 3 12 $ironHL; Px $t 12 12 $ironHL
Save $t "$Tex\block\schematicannon_iron.png"

# ---- barrel: cylinder shading across, brass muzzle and mid bands
$t = NewImg 16 16
$cols = @($ironDD, $ironD, $ironD, $ironM, $ironM, $ironL, $ironHL, $ironHL, $ironL, $ironM, $ironM, $ironM, $ironD, $ironD, $ironDD, $ironDD)
for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 16; $y++) { Px $t $x $y $cols[$x] } }
$bcols = @($brassDD, $brassD, $brassM, $brassM, $brassL, $brassL, $brassHL, $brassHL, $brassL, $brassM, $brassM, $brassM, $brassD, $brassD, $brassDD, $brassDD)
foreach ($row in @(0, 1, 9)) { for ($x = 0; $x -lt 16; $x++) { Px $t $x $row $bcols[$x] } }
for ($x = 0; $x -lt 16; $x++) { Px $t $x 2 $ironDD; Px $t $x 10 $ironDD }
Save $t "$Tex\block\schematicannon_barrel.png"


# ---- barrel side: same as the barrel but rotated a quarter turn (bands across the length of a horizontal barrel)
$src = [System.Drawing.Bitmap]::FromFile("$Tex\block\schematicannon_barrel.png")
$t = NewImg 16 16
for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 16; $y++) { Px $t $x $y $src.GetPixel($y, 15 - $x) } }
$src.Dispose()
Save $t "$Tex\block\schematicannon_barrel_side.png"
# ---- barrel muzzle (end face)
$t = NewImg 16 16
Fill $t 0 0 15 15 $brassM
Bevel $t 0 0 15 15 $brassHL $brassD
for ($x = 0; $x -lt 16; $x++) { for ($y = 0; $y -lt 16; $y++) {
    $d = [Math]::Sqrt([Math]::Pow($x - 7.5, 2) + [Math]::Pow($y - 7.5, 2))
    if ($d -lt 5.2) { Px $t $x $y $ironDD } elseif ($d -lt 6.3) { Px $t $x $y $ironD }
} }
Save $t "$Tex\block\schematicannon_muzzle.png"

# ---- wood (bottom of cannon, table posts)
function Planks([string]$name, $base, $dark, $seam, $light, [bool]$vertical) {
    $t = NewImg 16 16
    NoiseFill $t 0 0 15 15 $base 5
    for ($i = 0; $i -lt 16; $i++) {
        foreach ($s in @(3, 7, 11, 15)) { if ($vertical) { Px $t $s $i $seam } else { Px $t $i $s $seam } }
        foreach ($s in @(0, 4, 8, 12)) { if ($vertical) { Px $t $s $i $light } else { Px $t $i $s $light } }
    }
    foreach ($g in @(@(1, 2), @(5, 9), @(9, 4), @(13, 12), @(2, 13), @(10, 1))) {
        if ($vertical) { Px $t $g[0] $g[1] $dark; Px $t $g[0] ($g[1] + 1) $dark } else { Px $t $g[1] $g[0] $dark; Px $t ($g[1] + 1) $g[0] $dark }
    }
    Save $t "$Tex\block\$name.png"
}
Planks 'schematicannon_wood' $woodM $woodD $woodDD $woodL $false
Planks 'schematic_table_wood' $woodM $woodD $woodDD $woodL $true

# ---- table top: wooden board with a pinned blueprint
$t = NewImg 16 16
NoiseFill $t 0 0 15 15 $woodM 4
Outline $t 0 0 15 15 $woodDD
Bevel $t 1 1 14 14 $woodL $woodD
Fill $t 2 3 13 14 $bpM
for ($i = 2; $i -le 13; $i += 3) { Fill $t $i 3 $i 14 $bpD; }
for ($j = 5; $j -le 14; $j += 3) { Fill $t 2 $j 13 $j $bpD }
Grid $t 4 6 @(
    '...ww...',
    '..w..w..',
    '.w....w.',
    'wwwwwwww',
    '.w....w.',
    '.w.ww.w.',
    '.wwwwww.') (Pal 'w' $bpLine)
Fill $t 2 3 13 3 $bpL
Fill $t 13 3 13 14 $bpDD; Fill $t 2 14 13 14 $bpDD
Fill $t 6 1 9 3 $brassM; Fill $t 6 1 9 1 $brassHL; Fill $t 6 3 9 3 $brassD; Px $t 7 2 $brassDD; Px $t 8 2 $brassDD
Save $t "$Tex\block\schematic_table_top.png"

# ---- creative crate: violet crate with brass corner brackets and a star
$vio = Hex '7B47A8'; $vioD = Hex '583080'; $vioDD = Hex '2E1645'; $vioL = Hex '9A68C6'
$t = NewImg 16 16
NoiseFill $t 0 0 15 15 $vio 5
for ($i = 0; $i -lt 16; $i++) { Px $t $i 5 $vioD; Px $t $i 10 $vioD; Px $t $i 1 $vioL; Px $t $i 6 $vioL; Px $t $i 11 $vioL }
Outline $t 0 0 15 15 $vioDD
foreach ($c in @(@(0, 0, 1, 1), @(15, 0, -1, 1), @(0, 15, 1, -1), @(15, 15, -1, -1))) {
    for ($k = 0; $k -lt 4; $k++) { Px $t ($c[0] + $c[2] * $k) $c[1] $brassL; Px $t $c[0] ($c[1] + $c[3] * $k) $brassL }
    Px $t ($c[0] + $c[2]) ($c[1] + $c[3]) $brassD
}
Grid $t 5 5 @(
    '...y...',
    '...y...',
    '.yyYyy.',
    'yyYWYyy',
    '.yyYyy.',
    '...y...',
    '...y...') (Pal 'y' $brassM 'Y' $brassL 'W' $brassHL)
Save $t "$Tex\block\creative_crate.png"

# ---- items -----------------------------------------------------------------------------
$pal = Pal 'o' $papDD 'R' $papHL 'r' $papM 'd' $papD 'P' $papL 'p' $papM `
    'B' $bpM 'b' $bpD 'l' $bpL 'w' $bpLine 'k' $bpDD `
    'F' (Hex 'F4F4F0') 'f' (Hex 'C9C9C2') 's' (Hex '3A3530') 'n' (Hex '1A1A1A') 'g' $brassM
$scrollTop = @(
    '................',
    '..oooooooooooo..',
    '.oRRRRRRRRRRRRo.',
    '.orrrrrrrrrrrdo.',
    '..oddddddddddo..')
$scrollBottom = @(
    '..oooooooooooo..',
    '.oRRRRRRRRRRRRo.',
    '.orrrrrrrrrrrdo.',
    '..oooooooooooo..',
    '................')

$t = NewImg 16 16
Grid $t 0 0 $scrollTop $pal
Grid $t 0 5 @(
    '..oPPPPPPPPPdo..',
    '..oPPpPPPPPPdo..',
    '..oPPPPPPPpPdo..',
    '..oPPPPPPPPPdo..',
    '..oPpPPPPPPPdo..',
    '..oPPPPPPPPPdo..') $pal
Grid $t 0 11 $scrollBottom $pal
Save $t "$Tex\item\empty_schematic.png"

$blueprint = @(
    '..oBlBBlBBlBko..',
    '..oBBBwwBBBBko..',
    '..oBBwBBwBBBko..',
    '..oBwwwwwwBBko..',
    '..oBwBwwBwBBko..',
    '..oBwwwwwwBBko..')
$t = NewImg 16 16
Grid $t 0 0 $scrollTop $pal
Grid $t 0 5 $blueprint $pal
Grid $t 0 11 $scrollBottom $pal
Save $t "$Tex\item\schematic.png"

$t = NewImg 16 16
Grid $t 0 0 $scrollTop $pal
Grid $t 0 5 $blueprint $pal
Grid $t 0 11 $scrollBottom $pal
Grid $t 0 0 @(
    '............FFF.',
    '...........FFFFf',
    '..........FFFsff',
    '.........FFFsff.',
    '........FFFsff..',
    '.......FFFsff...',
    '.......FFsff....',
    '.......Fsf......',
    '......ss........',
    '.....gs.........',
    '....ng..........',
    '................') $pal
Save $t "$Tex\item\schematic_and_quill.png"

Write-Host "block and item textures written to $Tex"
