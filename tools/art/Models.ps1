# Block/item models for the Schematicannon: a static base, a turning head (yoke) and a tilting barrel.
# The head and barrel are drawn by SchematicannonRenderer so the cannon aims at what it fires at.
param([Parameter(Mandatory = $true)][string]$Models)

function Clamp16([double]$v) { [Math]::Max(0, [Math]::Min(16, $v)) }
# Element with auto UVs (clamped to the texture). $tex: default texture key; $over: hashtable face -> texture key;
# $full: faces that should use the whole texture instead (long parts like the barrel).
function El($from, $to, [string]$tex, $over = @{}, [string[]]$full = @(), $rot = $null, [string[]]$skip = @()) {
    $x1, $y1, $z1 = $from; $x2, $y2, $z2 = $to
    $uv = @{
        north = @((16 - $x2), (16 - $y2), (16 - $x1), (16 - $y1)); south = @($x1, (16 - $y2), $x2, (16 - $y1))
        east  = @((16 - $z2), (16 - $y2), (16 - $z1), (16 - $y1)); west = @($z1, (16 - $y2), $z2, (16 - $y1))
        up    = @($x1, $z1, $x2, $z2); down = @($x1, (16 - $z2), $x2, (16 - $z1))
    }
    $faces = @()
    foreach ($f in 'north', 'south', 'east', 'west', 'up', 'down') {
        if ($skip -contains $f) { continue }
        $t = if ($over.ContainsKey($f)) { $over[$f] } else { $tex }
        $u = if ($full -contains $f) { @(0, 0, 16, 16) } else { $uv[$f] | ForEach-Object { Clamp16 $_ } }
        if ($u[0] -eq $u[2]) { $u[2] = [Math]::Min(16, $u[0] + 1) }
        if ($u[1] -eq $u[3]) { $u[3] = [Math]::Min(16, $u[1] + 1) }
        $faces += "`"$f`": { `"uv`": [$($u -join ', ')], `"texture`": `"#$t`" }"
    }
    $r = if ($rot) { ", `"rotation`": $rot" } else { '' }
    "    { `"from`": [$($from -join ', ')], `"to`": [$($to -join ', ')]$r, `"faces`": { $($faces -join ', ') } }"
}
function Model([string]$path, [string]$textures, [string[]]$elements, [string]$parent = 'minecraft:block/block') {
    $json = "{`n  `"parent`": `"$parent`",`n  `"textures`": { $textures },`n  `"elements`": [`n$($elements -join ",`n")`n  ]`n}`n"
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    [IO.File]::WriteAllText($path, $json)
}

$T = '"particle": "schematicannon:block/schematicannon_casing", "casing": "schematicannon:block/schematicannon_casing", "plate": "schematicannon:block/schematicannon_casing_plate", "brass": "schematicannon:block/schematicannon_brass", "iron": "schematicannon:block/schematicannon_iron", "barrel": "schematicannon:block/schematicannon_barrel", "barrel_side": "schematicannon:block/schematicannon_barrel_side", "muzzle": "schematicannon:block/schematicannon_muzzle", "wood": "schematicannon:block/schematicannon_wood"'

# --- static base: wooden plinth, riveted casing, brass turntable ring
$base = @(
    (El @(0, 0, 0) @(16, 3, 16) 'wood' @{ up = 'casing' }),
    (El @(1, 3, 1) @(15, 9, 15) 'plate' @{ up = 'casing'; down = 'casing' }),
    (El @(2, 9, 2) @(14, 10, 14) 'brass')
)

# --- head: iron turntable plus two yoke arms holding the trunnions (rotates around the block's vertical axis)
$head = @(
    (El @(3, 10, 3) @(13, 11.5, 13) 'iron'),
    (El @(2.5, 11.5, 6) @(4.5, 17, 10) 'brass'),
    (El @(11.5, 11.5, 6) @(13.5, 17, 10) 'brass'),
    (El @(1.5, 14.5, 7.5) @(2.5, 15.5, 8.5) 'iron'),
    (El @(13.5, 14.5, 7.5) @(14.5, 15.5, 8.5) 'iron')
)

# --- barrel: points north (-Z) when level; pivots on the trunnions at (8, 15, 8)
$barrel = @(
    (El @(5, 12, -7) @(11, 18, 13) 'barrel_side' @{ up = 'barrel'; down = 'barrel'; north = 'muzzle'; south = 'iron' } @('east', 'west', 'up', 'down')),
    (El @(4, 11, -10) @(12, 19, -7) 'brass' @{ north = 'muzzle' } @('north')),
    (El @(4.5, 11.5, 3) @(11.5, 18.5, 5) 'brass'),
    (El @(6, 13.5, 13) @(10, 16.5, 15.5) 'iron'),
    (El @(7, 14, 15.5) @(9, 16, 17) 'brass'),
    (El @(4.6, 14.5, 7) @(11.4, 15.5, 9) 'iron')
)

Model "$Models\block\schematicannon.json" $T $base
Model "$Models\block\schematicannon_head.json" $T $head
Model "$Models\block\schematicannon_barrel.json" $T $barrel

# --- item / inventory: everything together, barrel raised 22.5 degrees
$raised = @()
foreach ($e in $barrel) { $raised += ($e -replace '\], "faces"', '], "rotation": { "angle": 22.5, "axis": "x", "origin": [8, 15, 8] }, "faces"') }
Model "$Models\item\schematicannon.json" $T ($base + $head + $raised)
# the barrel sticks out of the block, so hold it smaller than a normal block
$item = [IO.File]::ReadAllText("$Models\item\schematicannon.json")
$display = '  "display": { "firstperson_righthand": { "rotation": [0, 45, 0], "translation": [0, 1, 0], "scale": [0.28, 0.28, 0.28] }, "firstperson_lefthand": { "rotation": [0, 225, 0], "translation": [0, 1, 0], "scale": [0.28, 0.28, 0.28] }, "thirdperson_righthand": { "rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3] }, "thirdperson_lefthand": { "rotation": [75, 225, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3] }, "ground": { "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2] }, "gui": { "rotation": [30, 225, 0], "scale": [0.55, 0.55, 0.55] } },'
[IO.File]::WriteAllText("$Models\item\schematicannon.json", $item.Replace('  "textures":', "$display`n  `"textures`":"))

# --- Advanced Atomicannon (electric): same shape, steel plinth and power ports on the casing sides
# Each port is a socket box centred on the face at (8, 8): its 8x8 face spans 4..12 and the orange square in its
# texture covers 5.5..10.5, exactly where an NTM cable arm (5.5..10.5 square) meets the block edge.
$TA = $T.Replace('block/schematicannon_wood', 'block/schematicannon_steel') +
    ', "port": "schematicannon:block/schematicannon_power_port", "bottom": "schematicannon:block/schematicannon_steel_bottom"'
$advBase = @(
    (El @(0, 0, 0) @(16, 3, 16) 'wood' @{ up = 'casing'; down = 'bottom' } @('down')),
    $base[1], $base[2],
    (El @(0, 4, 4) @(1.5, 12, 12) 'iron' @{ west = 'port' } @('west')),
    (El @(14.5, 4, 4) @(16, 12, 12) 'iron' @{ east = 'port' } @('east')),
    (El @(4, 4, 0) @(12, 12, 1.5) 'iron' @{ north = 'port' } @('north')),
    (El @(4, 4, 14.5) @(12, 12, 16) 'iron' @{ south = 'port' } @('south'))
)
Model "$Models\block\advanced_schematicannon.json" $TA $advBase
Model "$Models\item\advanced_schematicannon.json" $TA ($advBase + $head + $raised)
$item = [IO.File]::ReadAllText("$Models\item\advanced_schematicannon.json")
[IO.File]::WriteAllText("$Models\item\advanced_schematicannon.json", $item.Replace('  "textures":', "$display`n  `"textures`":"))

Write-Host "models written to $Models"
