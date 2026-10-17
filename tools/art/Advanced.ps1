# Textures for the Advanced Atomicannon (electric, HBM NTM power): steel plinth and casing panels with
# NTM-style red power ports where cables attach.
param([Parameter(Mandatory = $true)][string]$Tex)
. "$PSScriptRoot\ArtLib.ps1"

$brassHL = Hex 'F7DE8A'; $brassM = Hex 'C4953A'; $brassDD = Hex '5A3D14'
$steelHL = Hex 'B4B8BF'; $steelL = Hex '979BA2'; $steelM = Hex '7A7E85'; $steelD = Hex '5B5E64'; $steelDD = Hex '35373B'
$andes = Hex '8F8E89'
$ironHL = Hex '8C9098'; $ironL = Hex '6D7179'; $ironM = Hex '54575E'; $ironD = Hex '3C3E43'; $ironDD = Hex '242528'
$portHL = Hex 'F2A15C'; $portL = Hex 'E8762E'; $portM = Hex 'D2541E'; $portD = Hex '9E3414'
$hazY = Hex 'E3B53A'; $hazK = Hex '2A2A2C'

function Rivet($img, [int]$x, [int]$y) { Px $img $x $y $brassHL; Px $img ($x + 1) $y $brassM; Px $img $x ($y + 1) $brassM; Px $img ($x + 1) ($y + 1) $brassDD }

# A power port like NTM's machines: dark frame, orange square with a bright core. 6x6 at (x, y).
function Port($img, [int]$x, [int]$y) {
    Fill $img $x $y ($x + 5) ($y + 5) $ironDD
    Fill $img ($x + 1) ($y + 1) ($x + 4) ($y + 4) $portM
    Bevel $img ($x + 1) ($y + 1) ($x + 4) ($y + 4) $portL $portD
    Fill $img ($x + 2) ($y + 2) ($x + 3) ($y + 3) $portL
    Px $img ($x + 2) ($y + 2) $portHL
    Px $img ($x + 3) ($y + 3) $portM
}

# ---- casing panel with a power port (the base's side faces show rows 7..12)
$t = NewImg 16 16
Fill $t 0 0 15 15 $steelM
Outline $t 0 0 15 15 $steelDD
Bevel $t 1 1 14 14 $steelHL $steelD
Bevel $t 3 3 12 12 $steelDD $steelL
NoiseFill $t 4 4 11 11 $andes 6
Port $t 5 7
Rivet $t 1 1; Rivet $t 13 1; Rivet $t 1 13; Rivet $t 13 13
Save $t "$Tex\block\schematicannon_casing_port.png"

# ---- steel plinth with a hazard stripe (replaces the basic cannon's wooden plinth)
$t = NewImg 16 16
NoiseFill $t 0 0 15 15 $ironM 4
Outline $t 0 0 15 15 $ironDD
Bevel $t 1 1 14 14 $ironHL $ironD
for ($x = 0; $x -lt 16; $x++) {
    $c = if ((($x + 16) % 4) -lt 2) { $hazY } else { $hazK }
    Px $t $x 13 $c; Px $t $x 14 $c
}
Fill $t 0 15 15 15 $ironDD
foreach ($x in 2, 7, 12) { Px $t $x 2 $ironHL; Px $t ($x + 1) 3 $ironD }
Save $t "$Tex\block\schematicannon_steel.png"

# ---- power port face for the 8x8 px sockets (block 4..12): one texel = half a pixel, so the orange square is
# texels 3..12 = block 5.5..10.5, exactly the cross-section of an NTM cable arm.
function PortFace($img, [int]$ox, [int]$oy) {
    Fill $img $ox $oy ($ox + 15) ($oy + 15) $steelM
    Outline $img $ox $oy ($ox + 15) ($oy + 15) $ironDD
    Bevel $img ($ox + 1) ($oy + 1) ($ox + 14) ($oy + 14) $steelHL $steelD
    Fill $img ($ox + 3) ($oy + 3) ($ox + 12) ($oy + 12) $portM
    Bevel $img ($ox + 3) ($oy + 3) ($ox + 12) ($oy + 12) $portD $portD
    Bevel $img ($ox + 4) ($oy + 4) ($ox + 11) ($oy + 11) $portL $portM
    Fill $img ($ox + 6) ($oy + 6) ($ox + 9) ($oy + 9) $portL
    Fill $img ($ox + 6) ($oy + 6) ($ox + 7) ($oy + 7) $portHL
}
$t = NewImg 16 16
PortFace $t 0 0
Save $t "$Tex\block\schematicannon_power_port.png"

# ---- plinth underside (32x32 = half-pixel texels) with the bottom port centred: socket 4..12 -> texels 8..23
$t = NewImg 32 32
NoiseFill $t 0 0 31 31 $ironM 4
Outline $t 0 0 31 31 $ironDD
Bevel $t 1 1 30 30 $ironHL $ironD
PortFace $t 8 8
Save $t "$Tex\block\schematicannon_steel_bottom.png"

Write-Host "advanced cannon textures written to $Tex"
