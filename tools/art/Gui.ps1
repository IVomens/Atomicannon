# GUI textures and icon sheet. Original art; layout coordinates must match the screen classes.
param([Parameter(Mandatory = $true)][string]$Tex)
. "$PSScriptRoot\ArtLib.ps1"

$outlineC = Hex '221D17'
$brassHL = Hex 'F7DE8A'; $brassL = Hex 'E6BC55'; $brassM = Hex 'C4953A'; $brassD = Hex '8C6224'; $brassDD = Hex '5A3D14'
$steelHL = Hex 'C6C9CE'; $steelM = Hex '8A8D93'; $steelD = Hex '55585E'
$bodyTop = Hex 'B2AEA5'; $bodyBot = Hex '96928A'
$recessDark = Hex '3A3631'; $recessLight = Hex 'D6D1C6'
$slotFill = Hex '7A766E'; $slotShade = Hex '67635C'
$screenFill = Hex '1E242C'; $screenLine = Hex '242C36'
$barFill = Hex '2A2826'

function Panel($img, [int]$x, [int]$y, [int]$w, [int]$h) {
    $x1 = $x + $w - 1; $y1 = $y + $h - 1
    for ($j = $y; $j -le $y1; $j++) {
        $t = ($j - $y) / [Math]::Max(1, $h - 1)
        $c = [System.Drawing.Color]::FromArgb(255, [int]($bodyTop.R + ($bodyBot.R - $bodyTop.R) * $t), [int]($bodyTop.G + ($bodyBot.G - $bodyTop.G) * $t), [int]($bodyTop.B + ($bodyBot.B - $bodyTop.B) * $t))
        for ($i = $x; $i -le $x1; $i++) { Px $img $i $j (Shade $c ($script:Rng.Next(-2, 3))) }
    }
    Outline $img $x $y $x1 $y1 $outlineC
    Bevel $img ($x + 1) ($y + 1) ($x1 - 1) ($y1 - 1) $brassHL $brassDD
    Bevel $img ($x + 2) ($y + 2) ($x1 - 2) ($y1 - 2) $brassL $brassD
    Outline $img ($x + 3) ($y + 3) ($x1 - 3) ($y1 - 3) (Hex '4A4236')
    Bevel $img ($x + 4) ($y + 4) ($x1 - 4) ($y1 - 4) (Hex 'CFCBC2') (Hex '86827A')
    foreach ($p in @(@(($x + 6), ($y + 6)), @(($x1 - 8), ($y + 6)), @(($x + 6), ($y1 - 8)), @(($x1 - 8), ($y1 - 8)))) {
        Px $img $p[0] $p[1] $steelHL; Px $img ($p[0] + 1) $p[1] $steelM; Px $img ($p[0] + 2) $p[1] $steelM
        Px $img $p[0] ($p[1] + 1) $steelM; Px $img ($p[0] + 1) ($p[1] + 1) $steelHL; Px $img ($p[0] + 2) ($p[1] + 1) $steelD
        Px $img $p[0] ($p[1] + 2) $steelM; Px $img ($p[0] + 1) ($p[1] + 2) $steelD; Px $img ($p[0] + 2) ($p[1] + 2) $steelD
    }
}
function Recess($img, [int]$x, [int]$y, [int]$w, [int]$h, $fill) {
    Fill $img $x $y ($x + $w - 1) ($y + $h - 1) $fill
    Fill $img $x $y ($x + $w - 2) $y $recessDark; Fill $img $x $y $x ($y + $h - 2) $recessDark
    Fill $img ($x + 1) ($y + $h - 1) ($x + $w - 1) ($y + $h - 1) $recessLight; Fill $img ($x + $w - 1) ($y + 1) ($x + $w - 1) ($y + $h - 1) $recessLight
}
# slot at the menu slot position (item drawn at x,y; frame is 18x18 at x-1,y-1)
function Slot($img, [int]$x, [int]$y) {
    Recess $img ($x - 1) ($y - 1) 18 18 $slotFill
    Fill $img $x $y ($x + 15) $y $slotShade; Fill $img $x $y $x ($y + 15) $slotShade
}
function Screen($img, [int]$x, [int]$y, [int]$w, [int]$h) {
    Recess $img $x $y $w $h $screenFill
    for ($j = $y + 2; $j -lt $y + $h - 1; $j += 2) { Fill $img ($x + 1) $j ($x + $w - 2) $j $screenLine }
}
function Arrow($img, [int]$x, [int]$y, $c, $cd) {
    Grid $img $x $y @(
        '....a....',
        '....aa...',
        'aaaaaaa..',
        'aaaaaaaa.',
        'aaaaaaa..',
        '....aa...',
        '....a....') (Pal 'a' $c)
    AutoOutline $img ($x - 1) ($y - 1) 11 9 $cd
}
function GhostItem($img, [int]$x, [int]$y, [string[]]$rows) { Grid $img $x $y $rows (Pal 'x' (Hex '8A867E') 'X' (Hex '96928A')) }
function Label($img, [int]$x, [int]$y, [int]$w) {
    # engraved brass name plate
    Fill $img $x $y ($x + $w - 1) ($y + 10) $brassM
    Bevel $img $x $y ($x + $w - 1) ($y + 10) $brassHL $brassDD
    Bevel $img ($x + 1) ($y + 1) ($x + $w - 2) ($y + 9) $brassL $brassD
}

$sheetItemsPowder = @(
    '................', '................', '................', '................',
    '................', '................', '................', '.......xx.......',
    '.....xxXXxx.....', '....xXXXXXXx....', '...xXXXXXXXXx...', '..xXXXXXXXXXXx..',
    '..xxxxxxxxxxxx..', '................', '................', '................')
$sheetItemsScroll = @(
    '................', '..xxxxxxxxxxxx..', '.xXXXXXXXXXXXXx.', '..xxxxxxxxxxxx..',
    '...xXXXXXXXXx...', '...xXxxxxxxXx...', '...xXXXXXXXXx...', '...xXxxxxxxXx...',
    '...xXXXXXXXXx...', '...xXxxxxxXXx...', '...xXXXXXXXXx...', '..xxxxxxxxxxxx..',
    '.xXXXXXXXXXXXXx.', '..xxxxxxxxxxxx..', '................', '................')
$sheetItemsBook = @(
    '................', '................', '...xxxxxxxxxx...', '...xXXXXXXXXXx..',
    '...xXxxxxxxXXx..', '...xXXXXXXXXXx..', '...xXxxxxxXXXx..', '...xXXXXXXXXXx..',
    '...xXXXXXXXXXx..', '...xXXXXXXXXXx..', '...xXXXXXXXXXx..', '...xXXXXXXXXXx..',
    '...xxxxxxxxxxxx.', '................', '................', '................')

# ------------------------------------------------------------------ schematicannon (256x256)
$g = NewImg 256 256
Panel $g 0 0 214 146
Label $g 5 4 118   # wide enough for "Advanced Atomicannon"
foreach ($s in @(@(15, 19), @(171, 65), @(134, 19), @(174, 19), @(15, 65))) { Slot $g $s[0] $s[1] }
GhostItem $g 15 19 $sheetItemsPowder
GhostItem $g 15 65 $sheetItemsScroll
GhostItem $g 134 19 $sheetItemsBook
Recess $g 36 22 86 8 $barFill                      # fuel
Recess $g 153 22 18 8 $barFill                     # checklist progress
Screen $g 36 44 132 16                             # status
Slot $g 187 44                                     # missing item
Recess $g 36 68 132 10 $barFill                    # schematic progress
Arrow $g 155 32 $brassL $brassDD
# Advanced Atomicannon: battery slot (drawn over the gunpowder slot), 18x18 frame at (229,9)
$sheetItemsBattery = @(
    '................', '......xxxx......', '.....xXXXXx.....', '....xxxxxxxx....',
    '....xXXXXXXx....', '....xXXXxXXx....', '....xXXxxXXx....', '....xXxxxxXx....',
    '....xXXXxxXx....', '....xXXxxXXx....', '....xXXxXXXx....', '....xXXXXXXx....',
    '....xXXXXXXx....', '....xxxxxxxx....', '................', '................')
Slot $g 230 10
GhostItem $g 230 10 $sheetItemsBattery
Fill $g 10 86 203 86 (Hex '86827A'); Fill $g 10 87 203 87 (Hex 'CFCBC2')   # divider
# indicator lamp sockets under the two toggle buttons
foreach ($lx in @(110, 130)) { Recess $g ($lx + 5) 132 8 4 (Hex '2A2622') }
# inventory panel (shared style), player slots at (37,161)
Panel $g 0 150 178 90
for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 9; $c++) { Slot $g (8 + $c * 18) (157 + $r * 18) } }
for ($c = 0; $c -lt 9; $c++) { Slot $g (8 + $c * 18) (215) }
Save $g "$Tex\gui\schematicannon.png"

# ------------------------------------------------------------------ schematic table (256x256)
$g = NewImg 256 256
Panel $g 0 0 214 92
Label $g 5 4 96
Recess $g 28 24 158 16 (Hex '2C5A9E')
for ($i = 30; $i -lt 184; $i += 6) { Fill $g $i 25 $i 38 (Hex '3664AA') }
Fill $g 29 25 184 25 (Hex '4A7CC4')
Slot $g 21 59; Slot $g 166 59
GhostItem $g 21 59 $sheetItemsScroll
Arrow $g 72 63 $brassL $brassDD
Arrow $g 126 63 $brassL $brassDD
Recess $g 46 80 116 5 $barFill
Panel $g 0 150 178 90
for ($r = 0; $r -lt 3; $r++) { for ($c = 0; $c -lt 9; $c++) { Slot $g (8 + $c * 18) (157 + $r * 18) } }
for ($c = 0; $c -lt 9; $c++) { Slot $g (8 + $c * 18) (215) }
Save $g "$Tex\gui\schematic_table.png"

# ------------------------------------------------------------------ text prompt / edit panels (9-slice source)
$g = NewImg 64 64
Panel $g 0 0 64 64
Save $g "$Tex\gui\panel.png"

# ------------------------------------------------------------------ icons + buttons (256x256)
$I = NewImg 256 256
$white = Hex 'F2F2EE'; $grey = Hex 'A5A39D'; $stone = Hex '8C8A85'; $dark = Hex '1C1A17'
$pal = Pal 'w' $white 'g' $grey 's' $stone 'S' (Hex '6E6C67') 'y' $brassL 'Y' $brassHL 'd' $brassD 'r' (Hex 'E0453A') 'G' (Hex '5BC24A') `
    'b' (Hex '5C9BE6') 'B' (Hex '2F6DBF') 'c' (Hex '9C6A3A') 'C' (Hex '6E4524') 'k' (Hex '3A3530')
function Icon([int]$index, [string[]]$rows) {
    $x = ($index % 16) * 16; $y = [Math]::Floor($index / 16) * 16
    Grid $I $x $y $rows $pal
    AutoOutline $I $x $y 16 16 $dark
}
$E = '................'
Icon 0 @($E, $E, $E, '....ww..........', '....wwww........', '....wwwwww......', '....wwwwwwww....', '....wwwwwwwwww..',
    '....wwwwwwwwww..', '....wwwwwwww....', '....wwwwww......', '....wwww........', '....ww..........', $E, $E, $E)          # play
Icon 1 @($E, $E, $E, '....www..www....', '....www..www....', '....www..www....', '....www..www....', '....www..www....',
    '....www..www....', '....www..www....', '....www..www....', '....www..www....', '....www..www....', $E, $E, $E)          # pause
Icon 2 @($E, $E, $E, $E, '....wwwwwwww....', '....wwwwwwww....', '....wwwwwwww....', '....wwwwwwww....', '....wwwwwwww....',
    '....wwwwwwww....', '....wwwwwwww....', '....wwwwwwww....', $E, $E, $E, $E)                                          # stop
Icon 3 @($E, $E, '...ssssssss.....', '...sSsssssSs....', '...ssssssss.....', '...ssssssss.....', '...ssssssssyyyy.',
    '...sSsssssyYYYYy', '...ssssssyYYYYYy', '...ssssssyYYYYYy', '..........yYYYy.', '...........yyy..', $E, $E, $E, $E)   # don't replace solid (shield)
Icon 4 @($E, $E, $E, $E, '.sssss....sssss.', '.sSsss....sSsss.', '.sssss.w..sssss.', '.sssss.ww.sssss.', '.sssss.w..sssss.',
    '.sssss....sssss.', $E, $E, $E, $E, $E, $E)                                                                           # solid -> solid
Icon 5 @($E, $E, $E, $E, '.sssss....yyyyy.', '.sSsss....yYyyy.', '.sssss.w..yyyyy.', '.sssss.ww.yyyyy.', '.sssss.w..yyyyy.',
    '.sssss....yyyyy.', $E, $E, $E, $E, $E, $E)                                                                           # solid -> any
Icon 6 @($E, $E, $E, $E, '.sssss....g.g.g.', '.sSsss.........', '.sssss.w..g...g.', '.sssss.ww.......', '.sssss.w..g...g.',
    '.sssss....g.g.g.', $E, $E, $E, $E, $E, $E)                                                                           # solid -> empty
Icon 7 @($E, $E, $E, $E, '...w....w.......', '....w....w......', '.....w....w.....', '......w....w....', '.....w....w.....',
    '....w....w......', '...w....w.......', $E, $E, $E, $E, $E)                                                            # skip missing
Icon 8 @($E, $E, $E, '..cccccccccccc..', '..cCCCCCCCCCCc..', '..cccccccccccc..', '..CCCCCyyCCCCC..', '..cccccYdccccc..',
    '..cccccccccccc..', '..cccccccccccc..', '..cccccccccccc..', '..CCCCCCCCCCCC..', $E, $E, $E, $E)                          # protect block entities (chest)
Icon 9 @($E, $E, $E, '........y.......', '........yy......', '........yYy.....', '..yyyyyyyYYy....', '..yYYYYYYYYYy...',
    '..yYYYYYYYYYy...', '..yyyyyyyYYy....', '........yYy.....', '........yy......', '........y.......', $E, $E, $E)         # upload
Icon 10 @($E, $E, '.....wwwww.w....', '....ww....www...', '...ww.....www...', '...w.....wwww...', '...w............', '...w.........w..',
    '...w.........w..', '...........ww...', '...wwww....w....', '...www....ww....', '...www.wwww.....', '....w...........', $E, $E)    # refresh
Icon 11 @($E, $E, $E, '..ddddd.........', '..dyyyyd........', '..dyyyyyddddddd.', '..dYYYYYYYYYYYd.', '..dyyyyyyyyyyyd.',
    '..dyyyyyyyyyyyd.', '..dyyyyyyyyyyyd.', '..dyyyyyyyyyyyd.', '..ddddddddddddd.', $E, $E, $E, $E)                         # folder
Icon 12 @($E, $E, $E, '.........ww.....', '........ww......', '.......ww.......', '......ww........', '.....ww.........',
    '......ww........', '.......ww.......', '........ww......', '.........ww.....', $E, $E, $E, $E)                            # previous
Icon 13 @($E, $E, $E, '.....ww.........', '......ww........', '.......ww.......', '........ww......', '.........ww.....',
    '........ww......', '.......ww.......', '......ww........', '.....ww.........', $E, $E, $E, $E)                            # next
# tool icons (row 1)
Icon 16 @($E, '.......w........', '.....bbwbb......', '....b..w..b.....', '...b...w...b....', '...b..www..b....', '.wwwwwwBwwwwww..',
    '...b..www..b....', '...b...w...b....', '....b..w..b.....', '.....bbwbb......', '.......w........', $E, $E, $E, $E)     # deploy
Icon 17 @($E, '.......w........', '......www.......', '.....wwwww......', '.......w........', '..w....w....w...', '.ww....w....ww..',
    'wwwwwwwwwwwwwww.', '.ww....w....ww..', '..w....w....w...', '.......w........', '.....wwwww......', '......www.......', '.......w........', $E, $E)  # move xz
Icon 18 @($E, '.......w........', '......www.......', '.....wwwww......', '....wwwwwww.....', '.......w........', '.......w........',
    '.......w........', '.......w........', '.......w........', '....wwwwwww.....', '.....wwwww......', '......www.......', '.......w........', $E, $E)  # move y
Icon 19 @($E, $E, '.....yyyyy.y....', '....yy....yyy...', '...yy.....yyy...', '...y.....yyyy...', '...y............', '...y.........y..',
    '...y.........y..', '...........yy...', '...yyyy....y....', '...yyy....yy....', '...yyy.yyyy.....', '....y...........', $E, $E)    # rotate
Icon 20 @($E, $E, '.......g........', '...w...g...w....', '..ww.......ww...', '.www...g...www..', 'wwww.......wwww.', '.www...g...www..',
    '..ww.......ww...', '...w...g...w....', '.......g........', $E, $E, $E, $E, $E)                                           # mirror
Icon 21 @($E, $E, $E, '............GG..', '...........GG...', '..........GG....', '..G......GG.....', '..GG....GG......',
    '...GG..GG.......', '....GGGG........', '.....GG.........', $E, $E, $E, $E, $E)                                            # print (confirm)

# button frames 18x18 at y=64: normal, hover, pressed/selected, disabled
function Btn([int]$x, $top, $fill, $bottom) {
    Fill $I $x 64 ($x + 17) 81 $fill
    Outline $I $x 64 ($x + 17) 81 $outlineC
    Bevel $I ($x + 1) 65 ($x + 16) 80 $top $bottom
}
Btn 0 (Hex 'D9D5CC') (Hex 'A9A59D') (Hex '6F6B64')
Btn 18 $brassHL (Hex 'C9B27A') $brassD
Btn 36 (Hex '5B574F') (Hex '807C74') (Hex 'C9C4BA')
Btn 54 (Hex '8E8B85') (Hex '76736E') (Hex '5E5B57')
# lamps 6x2 at y=88: off, on
Fill $I 0 88 5 89 (Hex '4A3A20'); Fill $I 8 88 13 89 (Hex '9CF36A'); Px $I 9 88 (Hex 'E4FFD6')

# tool bar pieces at y=96: left cap (8x38), tile (50x38), right cap (8x38), selection frame (34x30) at y=136
$tb = NewImg 66 38
Panel $tb 0 0 66 38
for ($x = 0; $x -lt 8; $x++) { for ($y = 0; $y -lt 38; $y++) { Px $I $x (96 + $y) $tb.GetPixel($x, $y) } }
for ($x = 0; $x -lt 50; $x++) { for ($y = 0; $y -lt 38; $y++) { Px $I (8 + $x) (96 + $y) $tb.GetPixel(8 + ($x % 16) + 4, $y) } }
for ($x = 0; $x -lt 8; $x++) { for ($y = 0; $y -lt 38; $y++) { Px $I (58 + $x) (96 + $y) $tb.GetPixel(58 + $x, $y) } }
$tb.Dispose()
Recess $I 0 136 34 30 (Hex '6886C5')
Bevel $I 1 137 32 164 (Hex '8FA8DC') (Hex '4A64A0')
Save $I "$Tex\gui\icons.png"

Write-Host "gui textures written to $Tex\gui"
