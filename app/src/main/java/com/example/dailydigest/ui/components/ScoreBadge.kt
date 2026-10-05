package com.example.dailydigest.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScoreBadge(
    importance: Int,
    relevance: Int,
    modifier: Modifier = Modifier
) {
    val importanceColor = when {
        importance >= 8 -> Color(0xFFE65100) // Deep Orange
        importance >= 5 -> Color(0xFFF57C00) // Orange
        else -> Color(0xFF757575)
    }

    val relevanceColor = when {
        relevance >= 8 -> Color(0xFF1B5E20) // Deep Green
        relevance >= 5 -> Color(0xFF2E7D32) // Forest Green
        else -> Color(0xFF616161)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Importance Tag
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(importanceColor.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Importance score",
                tint = importanceColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Imp $importance/10",
                color = importanceColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Relevance Tag
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(relevanceColor.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.TrackChanges,
                contentDescription = "Relevance score",
                tint = relevanceColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "Rel $relevance/10",
                color = relevanceColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
