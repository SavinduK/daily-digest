package com.example.dailydigest.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dailydigest.data.local.entity.Topic
import com.example.dailydigest.ui.viewmodel.DigestViewModel
import com.example.dailydigest.ui.viewmodel.TopicDigestSummary

@Composable
fun AnalyticsScreen(
    viewModel: DigestViewModel
) {
    val selectedTopicForHistory by viewModel.selectedTopicForHistory.collectAsStateWithLifecycle()

    // If a topic is clicked, show that specific topic's full news history sorted newest first
    if (selectedTopicForHistory != null) {
        TopicNewsHistoryScreen(
            topic = selectedTopicForHistory!!,
            viewModel = viewModel,
            onBack = { viewModel.closeTopicHistory() }
        )
        return
    }

    val topicSummaries by viewModel.topicSummaries.collectAsStateWithLifecycle()
    val allGroups by viewModel.allGroupNames.collectAsStateWithLifecycle()
    val selectedGroupFilter by viewModel.selectedGroupFilter.collectAsStateWithLifecycle()
    val filteredSummaries by viewModel.filteredTopicSummaries.collectAsStateWithLifecycle()

    // Dialog state for modifying a topic's group or update timer
    var topicToConfigure by remember { mutableStateOf<Topic?>(null) }

    if (topicToConfigure != null) {
        TopicGroupTimerDialog(
            topic = topicToConfigure!!,
            existingGroups = allGroups,
            onSave = { group, freq ->
                viewModel.assignTopicToGroup(topicToConfigure!!.id, group)
                viewModel.setTopicFrequency(topicToConfigure!!.id, freq)
                topicToConfigure = null
            },
            onRemoveFromGroup = {
                viewModel.removeTopicFromGroup(topicToConfigure!!.id)
                topicToConfigure = null
            },
            onDismiss = { topicToConfigure = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("analytics_screen")
    ) {
        // Screen Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "TRACKED TOPICS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Curated Digest",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Plus icon button to add/manage topics
                IconButton(
                    onClick = { viewModel.setManagingTopics(true) },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("add_topic_top_bar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add new topic",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Trackable Topic Groups Filter Bar (e.g. Story Books, Tech & AI, Research)
        if (topicSummaries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All Topics" Chip
                FilterChip(
                    selected = selectedGroupFilter == null,
                    onClick = { viewModel.selectGroupFilter(null) },
                    label = {
                        Text(
                            text = "All Topics (${topicSummaries.size})",
                            fontWeight = if (selectedGroupFilter == null) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("group_filter_all")
                )

                // Individual Group Chips
                allGroups.forEach { group ->
                    val groupCount = topicSummaries.count { it.topic.groupName.equals(group, ignoreCase = true) }
                    FilterChip(
                        selected = selectedGroupFilter.equals(group, ignoreCase = true),
                        onClick = {
                            if (selectedGroupFilter.equals(group, ignoreCase = true)) {
                                viewModel.selectGroupFilter(null)
                            } else {
                                viewModel.selectGroupFilter(group)
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "$group ($groupCount)",
                                fontWeight = if (selectedGroupFilter.equals(group, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("group_filter_$group")
                    )
                }
            }
        }

        if (topicSummaries.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No Tracked Topics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Create your research topics or story book groups to start tracking news and updates.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.setManagingTopics(true) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("add_first_topic_button")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Your First Topic")
                        }
                    }
                }
            }
        } else {
            // List of short individual cards for each tracked topic
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredSummaries,
                    key = { it.topic.id }
                ) { summary ->
                    ShortTopicCard(
                        summary = summary,
                        onClick = { viewModel.openTopicHistory(summary.topic) },
                        onConfigure = { topicToConfigure = summary.topic }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.setManagingTopics(true) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_topics_bottom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add or Edit Tracked Topics")
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Short, compact individual card for a tracked topic with group & cadence chips.
 */
@Composable
private fun ShortTopicCard(
    summary: TopicDigestSummary,
    onClick: () -> Unit,
    onConfigure: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("topic_card_${summary.topic.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val initial = summary.topic.name.take(1).uppercase()
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.topic.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Group & Cadence Tags
                Row(
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Group pill (e.g. "Story Books")
                    if (!summary.topic.groupName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = summary.topic.groupName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Frequency badge
                    val freqLabel = when (summary.topic.updateFrequency.uppercase()) {
                        "WEEKLY" -> "Once a week"
                        "BIWEEKLY" -> "Once 2 weeks"
                        "MONTHLY" -> "Once a month"
                        else -> "Daily"
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (summary.latestItem != null) {
                    Text(
                        text = summary.latestItem.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "${summary.itemCount} update${if (summary.itemCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!summary.latestDate.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Latest: ${summary.latestDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No updates recorded yet • Tap to view",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Quick configure group/timer button
            IconButton(
                onClick = onConfigure,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("configure_topic_${summary.topic.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Configure topic group or timer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Navigation Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "View news for ${summary.topic.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Dialog to add/remove topic from a group and set its custom update timer.
 */
@Composable
private fun TopicGroupTimerDialog(
    topic: Topic,
    existingGroups: List<String>,
    onSave: (groupName: String?, frequency: String) -> Unit,
    onRemoveFromGroup: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGroup by remember { mutableStateOf(topic.groupName ?: "") }
    var customGroupInput by remember { mutableStateOf("") }
    var selectedFrequency by remember { mutableStateOf(topic.updateFrequency) }

    val suggestedGroups = remember(existingGroups) {
        val defaults = listOf("Story Books", "Tech & AI", "Research", "Gaming", "News")
        (defaults + existingGroups).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Topic Settings: ${topic.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Section 1: Topic Group
                Text(
                    text = "TOPIC GROUP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Group Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedGroups.forEach { grp ->
                        FilterChip(
                            selected = selectedGroup.equals(grp, ignoreCase = true),
                            onClick = {
                                selectedGroup = if (selectedGroup.equals(grp, ignoreCase = true)) "" else grp
                            },
                            label = { Text(grp, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Custom Group Name input
                OutlinedTextField(
                    value = customGroupInput,
                    onValueChange = {
                        customGroupInput = it
                        if (it.isNotBlank()) selectedGroup = it
                    },
                    placeholder = { Text("Or type new group (e.g. Story Books)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    shape = RoundedCornerShape(10.dp)
                )

                if (selectedGroup.isNotBlank()) {
                    TextButton(
                        onClick = {
                            selectedGroup = ""
                            customGroupInput = ""
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text("Remove from Group", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Section 2: Custom Update Timer
                Text(
                    text = "UPDATE TIMER CADENCE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Controls how often automated digests check this topic",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                val frequencies = listOf(
                    "DAILY" to "Daily",
                    "WEEKLY" to "Once a week",
                    "BIWEEKLY" to "Once 2 weeks",
                    "MONTHLY" to "Once a month"
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    frequencies.forEach { (freqKey, label) ->
                        FilterChip(
                            selected = selectedFrequency.equals(freqKey, ignoreCase = true),
                            onClick = { selectedFrequency = freqKey },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalGroup = selectedGroup.trim().ifBlank { null }
                    onSave(finalGroup, selectedFrequency)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
