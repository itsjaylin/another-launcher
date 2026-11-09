package com.anotherlauncher.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.anotherlauncher.data.HomeLayout

@Composable
fun PageIndicators(
    layout: HomeLayout,
    pager: androidx.compose.foundation.pager.PagerState,
    spreadCount: Int, perSpread: Int, wide: Boolean,
    editing: Boolean, onSelectSpread: (Int)->Unit, onOpenHub: ()->Unit,
    onSearchLibrary: ()->Unit, toggleLibrary: ()->Unit, isLibraryOpen: Boolean,
) {
    val ink = androidx.compose.ui.graphics.Color.White
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp).semantics { contentDescription = "Page indicators" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Hub
        IconButton(onClick=onOpenHub, Modifier.semantics{contentDescription="Hub"}) {
            GlyphIcon(Glyph.SEARCH, Modifier.size(19.dp), ink)
        }
        Spacer(Modifier.width(8.dp))
        // Dots for home spreads (only while settled on a spread)
        val onHome = !editing && pager.settledPage >= 1 && pager.settledPage <= spreadCount
        if (onHome) {
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center) {
                repeat(spreadCount) { s ->
                    val active = (pager.settledPage - 1) == s
                    Box(Modifier.size(if(active) 7.dp else 5.dp).background(
                        ink.copy(alpha=if(active).9f else.35f), RoundedCornerShape(50)))
                }
            }
        } else {
            Box(Modifier.height(30.dp).width(100.dp).clip(RoundedCornerShape(50)).glass(50, frosted=true)
                .clickable(role=Role.Button, onClickLabel="Search", onClick=onSearchLibrary)
                .padding(horizontal=14.dp), contentAlignment=Alignment.Center) {
                GlyphIcon(Glyph.SEARCH, Modifier.size(15.dp), ink)
                Spacer(Modifier.width(5.dp))
                Text("Search", fontSize=12.sp, color=ink)
            }
        }
        Spacer(Modifier.width(8.dp))
        // Library
        IconButton(onClick=toggleLibrary, Modifier.semantics{contentDescription="Library"}) {
            GlyphIcon(Glyph.APPS, Modifier.size(19.dp), ink.copy(alpha=if(isLibraryOpen) 1f else .65f))
        }
    }
}
