package com.example

// WM-FIX: Minimal pure PostScript Level 2 EPS 3.0 operator definitions
object PsProlog {
    val PROLOG: String = buildString {
        append("%%BeginProlog\n")
        append("/_m { moveto } bind def\n")
        append("/_l { lineto } bind def\n")
        append("/_c { curveto } bind def\n")
        append("/_h { closepath } bind def\n")
        append("/_n { newpath } bind def\n")
        append("/_f { fill } bind def\n")
        append("/_f* { eofill } bind def\n")
        append("/_s { stroke } bind def\n")
        append("/_b { gsave fill grestore stroke } bind def\n")
        append("/_b* { gsave eofill grestore stroke } bind def\n")
        append("/_w { setlinewidth } bind def\n")
        append("/_J { setlinecap } bind def\n")
        append("/_j { setlinejoin } bind def\n")
        append("/_M { setmiterlimit } bind def\n")
        append("/_d { setdash } bind def\n")
        append("/_rg { setrgbcolor } bind def\n")
        append("/_RG { setrgbcolor } bind def\n")
        append("/_gs { gsave } bind def\n")
        append("/_gr { grestore } bind def\n")
        append("/_W { clip } bind def\n")
        append("/_W* { eoclip } bind def\n")
        append("/_sh { systemdict /shfill known { shfill } { pop pop } ifelse } bind def\n")
        append("%%EndProlog\n\n")
    }
}
