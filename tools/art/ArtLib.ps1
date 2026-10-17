# Small pixel-art toolkit used by generate_art.ps1. All art in this mod is original.
Add-Type -AssemblyName System.Drawing

$script:Rng = New-Object System.Random 4242

function Hex([string]$h, [int]$a = 255) {
    $h = $h.TrimStart('#')
    [System.Drawing.Color]::FromArgb($a, [Convert]::ToInt32($h.Substring(0, 2), 16), [Convert]::ToInt32($h.Substring(2, 2), 16), [Convert]::ToInt32($h.Substring(4, 2), 16))
}
function Shade($c, [int]$d) {
    [System.Drawing.Color]::FromArgb($c.A, [Math]::Max(0, [Math]::Min(255, $c.R + $d)), [Math]::Max(0, [Math]::Min(255, $c.G + $d)), [Math]::Max(0, [Math]::Min(255, $c.B + $d)))
}
function NewImg([int]$w, [int]$h) {
    $b = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($x = 0; $x -lt $w; $x++) { for ($y = 0; $y -lt $h; $y++) { $b.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0)) } }
    $b
}
function Px($img, [int]$x, [int]$y, $c) { if ($x -ge 0 -and $y -ge 0 -and $x -lt $img.Width -and $y -lt $img.Height) { $img.SetPixel($x, $y, $c) } }
function Fill($img, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) { for ($x = $x0; $x -le $x1; $x++) { for ($y = $y0; $y -le $y1; $y++) { Px $img $x $y $c } } }
function NoiseFill($img, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c, [int]$amp) {
    for ($x = $x0; $x -le $x1; $x++) { for ($y = $y0; $y -le $y1; $y++) { Px $img $x $y (Shade $c ($script:Rng.Next(-$amp, $amp + 1))) } }
}
# Raised (light top-left, dark bottom-right) or sunken bevel border, 1px.
function Bevel($img, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $light, $dark) {
    Fill $img $x0 $y0 $x1 $y0 $light; Fill $img $x0 $y0 $x0 $y1 $light
    Fill $img $x0 $y1 $x1 $y1 $dark; Fill $img $x1 $y0 $x1 $y1 $dark
}
function Outline($img, [int]$x0, [int]$y0, [int]$x1, [int]$y1, $c) { Bevel $img $x0 $y0 $x1 $y1 $c $c }
# Case-sensitive palette: Pal 'a' $colorA 'A' $colorB ...
function Pal {
    $d = New-Object 'System.Collections.Generic.Dictionary[string,object]' ([StringComparer]::Ordinal)
    for ($i = 0; $i -lt $args.Count; $i += 2) { $d[[string]$args[$i]] = $args[$i + 1] }
    , $d
}
# Draws a grid of characters using a palette hashtable; '.' is skipped.
function Grid($img, [int]$ox, [int]$oy, [string[]]$rows, $pal) {
    for ($y = 0; $y -lt $rows.Length; $y++) {
        $r = $rows[$y]
        for ($x = 0; $x -lt $r.Length; $x++) {
            $ch = [string]$r[$x]
            if ($ch -ne '.' -and $pal.ContainsKey($ch)) { Px $img ($ox + $x) ($oy + $y) $pal[$ch] }
        }
    }
}
# Adds a 1px outline around every opaque pixel inside the given box (icon style).
function AutoOutline($img, [int]$x0, [int]$y0, [int]$w, [int]$h, $c) {
    $mark = @()
    for ($x = $x0; $x -lt $x0 + $w; $x++) { for ($y = $y0; $y -lt $y0 + $h; $y++) {
        if ($img.GetPixel($x, $y).A -ne 0) { continue }
        foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
            $nx = $x + $d[0]; $ny = $y + $d[1]
            if ($nx -ge $x0 -and $ny -ge $y0 -and $nx -lt $x0 + $w -and $ny -lt $y0 + $h -and $img.GetPixel($nx, $ny).A -gt 0 -and $img.GetPixel($nx, $ny) -ne $c) { $mark += , @($x, $y); break }
        }
    } }
    foreach ($m in $mark) { Px $img $m[0] $m[1] $c }
}
function Save($img, [string]$path) {
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $img.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $img.Dispose()
}
