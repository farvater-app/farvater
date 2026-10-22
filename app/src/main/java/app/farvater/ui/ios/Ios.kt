package app.farvater.ui.ios

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.farvater.ui.theme.Ios
import app.farvater.ui.theme.IosMotion
import app.farvater.ui.theme.IosType
import app.farvater.ui.theme.NothingLabel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.iosPress(
    enabled: Boolean = true,
    scaleTo: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) scaleTo else 1f,
        spring(dampingRatio = IosMotion.SnappyDamping, stiffness = IosMotion.SnappyStiffness),
        label = "press",
    )
    val alpha by animateFloatAsState(if (pressed) 0.75f else 1f, spring(stiffness = 700f), label = "dim")
    graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.iosRowPress(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    if (Ios.material) {
        combinedClickable(onLongClick = onLongClick, onClick = onClick)
    } else {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val c = Ios.colors
        val bg by animateColorAsState(
            if (pressed) c.highlight else Color.Transparent,
            if (pressed) snap() else tween(320),
            label = "row",
        )
        background(bg).combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    }
}

val SectionCorner = 24.dp

@Composable
@ReadOnlyComposable
fun sectionCorner(): Dp = when {
    Ios.origin -> 26.dp
    Ios.hyper -> 22.dp
    Ios.harmony -> 20.dp
    Ios.oneUi -> 26.dp
    Ios.oxygen -> 18.dp
    else -> SectionCorner
}

fun originRim(dark: Boolean): Brush = Brush.verticalGradient(
    if (dark) {
        listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f))
    } else {
        listOf(Color.White, Color(0xFF1B2350).copy(alpha = 0.08f))
    },
)

val SheetCorner: Dp
    @Composable @ReadOnlyComposable get() = if (Ios.material) 28.dp else 34.dp

@Composable
fun IosLargeTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Ios.colors
    val material = Ios.material
    if (Ios.oneUi) {
        val height = (LocalConfiguration.current.screenHeightDp * 0.3f).dp.coerceIn(190.dp, 300.dp)
        Column(
            modifier.fillMaxWidth().height(height).padding(start = 24.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                title,
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 41.sp),
                color = c.label,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(end = 12.dp),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().height(52.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = if (material) 12.dp else 16.dp,
                top = if (material) 28.dp else 12.dp,
                bottom = if (material) 14.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (material) MaterialTheme.typography.headlineLarge else IosType.largeTitle,
                color = c.label,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = if (material) MaterialTheme.typography.bodyMedium else IosType.subheadline,
                    color = c.secondaryLabel,
                    maxLines = 1,
                )
            }
        }
        trailing()
    }
}

@Composable
fun IosCompactBar(title: String, visible: Boolean, modifier: Modifier = Modifier, backdrop: GlassBackdrop? = null) {
    val c = Ios.colors
    val p by animateFloatAsState(if (visible) 1f else 0f, tween(180), label = "bar")
    if (p == 0f) return
    if (Ios.material) {
        Box(
            modifier.fillMaxWidth().height(64.dp).graphicsLayer { alpha = p }.background(c.bar),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = c.label,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer { alpha = p }
            .liquidGlass(
                backdrop,
                RectangleShape,
                GlassStyles.Chrome,
                tint = c.background.copy(alpha = if (c.dark) 0.55f else 0.6f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = IosType.headline,
            color = c.label,
            modifier = Modifier.graphicsLayer { translationY = (1f - p) * 8.dp.toPx() },
        )
    }
}

@Composable
fun IosSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    footerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ios.colors
    val material = Ios.material
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        when {
            header != null && Ios.nothing -> Text(
                header.uppercase(),
                style = NothingLabel,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            header != null && material -> Text(
                header,
                style = MaterialTheme.typography.titleSmall,
                color = if (Ios.hyper || Ios.harmony || Ios.oneUi || Ios.oxygen) c.secondaryLabel else c.tint,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            )
            header != null -> Text(
                header.uppercase(),
                style = IosType.footnote,
                color = c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 7.dp),
            )
            else -> Spacer(Modifier.height(if (material) 16.dp else 20.dp))
        }
        val shape = RoundedCornerShape(sectionCorner())
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (Ios.origin) Modifier.border(1.dp, originRim(c.dark), shape) else Modifier)
                .clip(shape)
                .background(c.cell),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = if (material) MaterialTheme.typography.bodySmall else IosType.footnote,
                color = footerColor ?: c.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (material) 8.dp else 7.dp),
            )
        }
    }
}

@Composable
fun IosDivider(start: Dp = 16.dp) {
    val c = Ios.colors
    if (Ios.material) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.background))
    } else {
        HorizontalDivider(Modifier.padding(start = start), thickness = 0.5.dp, color = c.separator)
    }
}

@Composable
fun IosIcon(icon: ImageVector, tint: Color, size: Dp = 29.dp) {
    val c = Ios.colors
    if (Ios.material) {
        Box(
            Modifier.size(size + 7.dp).clip(CircleShape).background(tint.copy(alpha = if (c.dark) 0.2f else 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.7f))
        }
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.64f))
        }
    }
}

@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    subtitleLines: Int = 2,
    titleLines: Int = 1,
    icon: ImageVector? = null,
    iconTint: Color = Ios.colors.tint,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    valueColor: Color? = null,
    chevron: Boolean = false,
    titleColor: Color? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Ios.colors
    val material = Ios.material
    val haptics = LocalHapticFeedback.current
    val showChevron = chevron && !material
    val titleStyle = if (material) MaterialTheme.typography.bodyLarge else IosType.body
    val subtitleStyle = if (material) MaterialTheme.typography.bodyMedium else IosType.footnote
    val valueStyle = if (material) MaterialTheme.typography.bodyMedium else IosType.body
    val vertical = if (material) 10.dp else 9.dp
    Row(
        modifier
            .fillMaxWidth()
            .then(
                when {
                    checked != null && onCheckedChange != null -> Modifier.toggleable(value = checked, role = Role.Switch) {
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onCheckedChange(it)
                    }
                    onClick != null -> Modifier.iosRowPress(onLongClick, onClick)
                    else -> Modifier
                },
            )
            .heightIn(min = if (material) 56.dp else 50.dp)
            .padding(start = 16.dp, end = if (showChevron) 10.dp else 16.dp, top = vertical, bottom = vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IosIcon(icon, iconTint)
            Spacer(Modifier.width(if (material) 16.dp else 14.dp))
        }
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = titleColor ?: c.label, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = subtitleStyle,
                    color = subtitleColor ?: c.secondaryLabel,
                    maxLines = subtitleLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = valueStyle, color = valueColor ?: c.secondaryLabel, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (checked != null) {
            Spacer(Modifier.width(8.dp))
            IosSwitch(checked, null)
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.tertiaryLabel,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun IosChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Ios.colors.tint,
) {
    val c = Ios.colors
    val radio = if (Ios.nothing) RadioButtonDefaults.colors(selectedColor = c.red) else RadioButtonDefaults.colors()
    val chosen = selected
    val choice = Modifier.semantics {
        this.selected = chosen
        role = Role.RadioButton
    }
    if (Ios.material && icon == null) {
        IosRow(
            title = title,
            modifier = choice,
            subtitle = subtitle,
            subtitleLines = Int.MAX_VALUE,
            onClick = onClick,
            leading = { RadioButton(selected = selected, onClick = null, colors = radio) },
        )
    } else {
        IosRow(
            title = title,
            modifier = choice,
            subtitle = subtitle,
            subtitleLines = Int.MAX_VALUE,
            onClick = onClick,
            icon = icon,
            iconTint = iconTint,
            trailing = {
                when {
                    Ios.material -> RadioButton(selected = selected, onClick = null, colors = radio)
                    selected -> Icon(Icons.Rounded.Check, contentDescription = null, tint = c.tint, modifier = Modifier.size(22.dp))
                }
            },
        )
    }
}

@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    if (Ios.material) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange?.let { change ->
                {
                    haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    change(it)
                }
            },
            modifier = modifier,
            colors = if (Ios.nothing) {
                SwitchDefaults.colors(
                    checkedTrackColor = c.red,
                    checkedThumbColor = Color.White,
                    checkedBorderColor = c.red,
                    uncheckedTrackColor = Color.Transparent,
                    uncheckedThumbColor = c.secondaryLabel,
                    uncheckedBorderColor = c.secondaryLabel,
                )
            } else {
                SwitchDefaults.colors()
            },
        )
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val progress by animateFloatAsState(
        if (checked) 1f else 0f,
        spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "switch",
    )
    val thumbWidth by animateDpAsState(
        if (pressed) 44.dp else 37.dp,
        spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "thumb",
    )
    val glass by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 500f), label = "glass")
    Box(
        modifier
            .size(62.dp, 28.dp)
            .clip(CircleShape)
            .background(lerp(c.fill, c.green, progress))
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier.toggleable(
                        value = checked,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Switch,
                        onValueChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                            onCheckedChange(it)
                        },
                    )
                },
            )
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(((58.dp - thumbWidth).toPx() * progress).roundToInt(), 0) }
                .size(thumbWidth, 24.dp)
                .graphicsLayer {
                    scaleX = 1f + glass * 0.04f
                    scaleY = 1f + glass * 0.1f
                }
                .shadow(3.dp, CircleShape)
                .background(Color.White.copy(alpha = 1f - glass * 0.3f), CircleShape)
                .border(1.dp, Brush.linearGradient(glassRim(c.dark)), CircleShape),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val haptics = LocalHapticFeedback.current
    val pick: (Int) -> Unit = { i ->
        if (i != selected) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onSelect(i)
        }
    }
    if (Ios.material) {
        val nothing = Ios.nothing
        SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == selected,
                    onClick = { pick(i) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    icon = { if (options.size <= 3 && !nothing) SegmentedButtonDefaults.Icon(i == selected) },
                    label = {
                        if (nothing) {
                            Text(label.uppercase(), style = NothingLabel.copy(fontSize = 10.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    },
                )
            }
        }
        return
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(c.fill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(
            segment * selected,
            spring(dampingRatio = IosMotion.SoftDamping, stiffness = 480f),
            label = "segment",
        )
        Box(
            Modifier
                .offset { IntOffset(x.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, CircleShape)
                .background(c.segmentThumb, CircleShape)
                .border(0.75.dp, Brush.linearGradient(glassRim(c.dark)), CircleShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { pick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = IosType.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = c.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun IosSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val material = Ios.material
    val textStyle = if (material) MaterialTheme.typography.bodyLarge else IosType.body
    Row(
        modifier
            .fillMaxWidth()
            .height(if (material) 56.dp else 44.dp)
            .clip(CircleShape)
            .background(
                if (material) MaterialTheme.colorScheme.surfaceContainerHigh else c.fill.copy(alpha = if (c.dark) 0.7f else 1f),
            )
            .padding(horizontal = if (material) 16.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = null,
            tint = c.secondaryLabel,
            modifier = Modifier.size(if (material) 24.dp else 20.dp),
        )
        Spacer(Modifier.width(if (material) 12.dp else 8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.secondaryLabel, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = textStyle.copy(color = c.label),
                cursorBrush = SolidColor(c.tint),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Rounded.Cancel,
                contentDescription = "Очистить",
                tint = c.secondaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

@Composable
fun IosTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = Ios.colors
    val material = Ios.material
    val shape = RoundedCornerShape(if (material) 12.dp else 10.dp)
    val textStyle = if (material) MaterialTheme.typography.bodyLarge else IosType.subheadline
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.cell)
            .border(if (material) 1.dp else 0.5.dp, if (material) c.tertiaryLabel else c.separator, shape)
            .padding(horizontal = if (material) 16.dp else 12.dp, vertical = if (material) 14.dp else 10.dp),
    ) {
        if (value.isEmpty()) Text(placeholder, style = textStyle, color = c.tertiaryLabel, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(color = c.label),
            cursorBrush = SolidColor(c.tint),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

enum class IosButtonStyle { Filled, Tinted, Plain }

@Composable
fun IosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: IosButtonStyle = IosButtonStyle.Filled,
    color: Color = Ios.colors.tint,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val c = Ios.colors
    if (Ios.material) {
        val scheme = MaterialTheme.colorScheme
        val brand = color == c.tint
        val outlined = Ios.nothing && style == IosButtonStyle.Tinted
        val bg = when {
            outlined -> Color.Transparent
            style == IosButtonStyle.Filled -> if (brand) scheme.primary else color
            style == IosButtonStyle.Tinted -> if (brand) scheme.secondaryContainer else color.copy(alpha = 0.16f)
            else -> Color.Transparent
        }
        val fg = when {
            outlined -> color
            style == IosButtonStyle.Filled -> if (brand) scheme.onPrimary else Color.White
            style == IosButtonStyle.Tinted -> if (brand) scheme.onSecondaryContainer else color
            else -> color
        }
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
        Row(
            modifier
                .heightIn(min = 48.dp)
                .graphicsLayer {
                    alpha = if (enabled) 1f else 0.38f
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(bg)
                .then(if (outlined) Modifier.border(1.5.dp, color, CircleShape) else Modifier)
                .clickable(interactionSource = press, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            if (Ios.nothing) {
                Text(text.uppercase(), style = NothingLabel.copy(fontSize = 13.sp, letterSpacing = 1.6.sp), color = fg, maxLines = 1)
            } else {
                Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = fg, maxLines = 1)
            }
        }
        return
    }
    val bg = when (style) {
        IosButtonStyle.Filled -> color
        IosButtonStyle.Tinted -> color.copy(alpha = 0.16f)
        IosButtonStyle.Plain -> Color.Transparent
    }
    val fg = if (style == IosButtonStyle.Filled) Color.White else color
    Row(
        modifier
            .heightIn(min = 50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .iosPress(enabled = enabled, onClick = onClick)
            .clip(CircleShape)
            .background(bg)
            .then(
                if (style == IosButtonStyle.Plain) {
                    Modifier
                } else {
                    Modifier.border(1.dp, Brush.linearGradient(glassRim(c.dark)), CircleShape)
                },
            )
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = IosType.headline, color = fg, maxLines = 1)
    }
}

@Composable
fun IosCircleButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = Ios.colors.tint) {
    val c = Ios.colors
    if (Ios.nothing) {
        Box(
            Modifier
                .size(40.dp)
                .iosPress(scaleTo = 0.9f, onClick = onClick)
                .clip(CircleShape)
                .border(1.dp, c.separator, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
        }
        return
    }
    if (Ios.material) {
        val scheme = MaterialTheme.colorScheme
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.secondaryContainer)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (tint == c.tint) scheme.onSecondaryContainer else tint,
                modifier = Modifier.size(22.dp),
            )
        }
        return
    }
    Box(
        Modifier
            .size(40.dp)
            .iosPress(scaleTo = 0.88f, onClick = onClick)
            .liquidGlass(null, CircleShape, GlassStyles.Control, fallback = glassControlFill(c.dark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(21.dp))
    }
}

@Composable
fun IosGrabber(modifier: Modifier = Modifier) {
    val c = Ios.colors
    if (Ios.material) {
        Box(modifier.size(32.dp, 4.dp).clip(CircleShape).background(c.secondaryLabel.copy(alpha = 0.4f)))
    } else {
        Box(modifier.size(36.dp, 5.dp).clip(CircleShape).background(c.tertiaryLabel))
    }
}

@Composable
fun IosPushedPage(
    title: String,
    backTitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ios.colors
    val scroll = rememberScrollState()
    EdgeHaptics(scroll)
    if (Ios.material) {
        Column(modifier.fillMaxSize().background(c.background)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (scroll.value > 4) c.bar else c.background)
                    .height(64.dp)
                    .padding(start = 4.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = backTitle, tint = c.label)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(scroll), content = content)
        }
        return
    }
    val backdrop = rememberGlassBackdrop()
    Box(modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().glassSource(backdrop).verticalScroll(scroll)) {
            Spacer(Modifier.height(56.dp))
            content()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .then(
                    if (scroll.value > 4) {
                        Modifier.liquidGlass(
                            backdrop,
                            RectangleShape,
                            GlassStyles.Chrome,
                            tint = c.background.copy(alpha = if (c.dark) 0.55f else 0.6f),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .iosPress(scaleTo = 0.88f, onClick = onBack)
                    .liquidGlass(null, CircleShape, GlassStyles.Control, fallback = glassControlFill(c.dark)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = backTitle,
                    tint = c.label,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                title,
                style = IosType.headline,
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 64.dp),
            )
        }
    }
}

@Composable
fun IosSheet(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Ios.colors
    val material = Ios.material
    val corner = SheetCorner
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 300f)) }
    val close: () -> Unit = {
        scope.launch {
            slide.animateTo(1f, spring(dampingRatio = 1f, stiffness = 520f))
            onClose()
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f * (1f - slide.value)))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 10.dp)
                    .graphicsLayer { translationY = size.height * slide.value }
                    .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
                    .background(c.background),
            ) {
                if (material) MaterialSheetHeader(title, close) else GlassSheetHeader(title, close)
                content()
            }
        }
    }
}

@Composable
fun IosActionPanel(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.(close: (then: () -> Unit) -> Unit) -> Unit,
) {
    val c = Ios.colors
    val material = Ios.material
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    var height by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f)) }
    val close: (() -> Unit) -> Unit = { then ->
        then()
        scope.launch {
            slide.animateTo(1f, spring(dampingRatio = 1f, stiffness = 600f))
            onDismiss()
        }
    }
    val shape = RoundedCornerShape(if (material) 28.dp else 34.dp)
    val drag = rememberDraggableState { delta ->
        scope.launch { slide.snapTo((slide.value + delta / height).coerceAtLeast(0f)) }
    }
    Dialog(onDismissRequest = { close {} }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (!material) BlurBehind(16.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f * (1f - slide.value)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { close {} },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(10.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height.coerceAtLeast(1) }
                    .graphicsLayer { translationY = (size.height + 40.dp.toPx()) * slide.value }
                    .draggable(
                        drag,
                        Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (slide.value > 0.3f || velocity > 1500f) {
                                close {}
                            } else {
                                slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 420f))
                            }
                        },
                    )
                    .clip(shape)
                    .background(if (material) MaterialTheme.colorScheme.surfaceContainerHigh else c.cellRaised)
                    .then(if (material) Modifier else Modifier.border(1.dp, Brush.linearGradient(glassRim(c.dark)), shape))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IosGrabber()
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    style = if (material) MaterialTheme.typography.titleMedium else IosType.headline,
                    color = c.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = IosType.footnote,
                        color = c.secondaryLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                content(close)
            }
        }
    }
}

@Composable
private fun GlassSheetHeader(title: String, onDone: () -> Unit) {
    val c = Ios.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(6.dp))
        IosGrabber()
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            Text(
                title,
                style = IosType.headline,
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 72.dp),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .size(40.dp)
                    .iosPress(scaleTo = 0.88f, onClick = onDone)
                    .liquidGlass(null, CircleShape, GlassStyles.Control, fallback = c.tint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Готово", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MaterialSheetHeader(title: String, onClose: () -> Unit) {
    val c = Ios.colors
    Column(Modifier.fillMaxWidth()) {
        IosGrabber(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp))
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Закрыть", tint = c.label)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = c.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun IosSpinner(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = Ios.colors.secondaryLabel) {
    if (Ios.nothing) {
        val fg = Ios.colors.label
        val head by rememberInfiniteTransition(label = "dots").animateFloat(
            initialValue = 0f,
            targetValue = 8f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "head",
        )
        Canvas(modifier.size(size)) {
            val r = this.size.minDimension / 2
            val dot = r * 0.2f
            for (i in 0 until 8) {
                val behind = ((head - i) % 8f + 8f) % 8f
                val angle = Math.toRadians(i * 45.0 - 90)
                drawCircle(
                    color = fg.copy(alpha = (1f - behind / 8f).coerceIn(0.15f, 1f)),
                    radius = dot,
                    center = Offset(center.x + kotlin.math.cos(angle).toFloat() * (r - dot), center.y + kotlin.math.sin(angle).toFloat() * (r - dot)),
                )
            }
        }
        return
    }
    if (Ios.material) {
        CircularProgressIndicator(
            modifier = modifier.size(size),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.5.dp,
        )
        return
    }
    val step by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "step",
    )
    Canvas(modifier.size(size).rotate(step.toInt() * 45f)) {
        val r = this.size.minDimension / 2
        val stroke = r * 0.22f
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0 - 90)
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = color.copy(alpha = 1f - i * 0.11f),
                start = Offset(center.x + cos * r * 0.45f, center.y + sin * r * 0.45f),
                end = Offset(center.x + cos * (r - stroke / 2), center.y + sin * (r - stroke / 2)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun IosProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Ios.colors.tint) {
    val c = Ios.colors
    val material = Ios.material
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = 200f), label = "progress")
    if (Ios.nothing) {
        val lit = c.label
        val off = c.separator
        val accent = c.red
        Canvas(modifier.fillMaxWidth().height(6.dp)) {
            val step = 9.dp.toPx()
            val count = (size.width / step).toInt().coerceAtLeast(1)
            val gap = (size.width - step * (count - 1)) / 2
            val litCount = animated * count
            for (i in 0 until count) {
                val on = (litCount - i).coerceIn(0f, 1f)
                val head = on > 0f && i + 1 >= litCount && animated < 1f
                drawCircle(
                    color = if (head) accent else lerp(off, lit, on),
                    radius = size.height / 2,
                    center = Offset(gap + i * step, size.height / 2),
                )
            }
        }
        return
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(if (material) 6.dp else 5.dp)
            .clip(CircleShape)
            .background(if (material) color.copy(alpha = 0.22f) else c.fill),
    ) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(CircleShape).background(color))
    }
}

enum class AlertRole { Default, Preferred, Cancel, Destructive }

@Immutable
data class AlertAction(val text: String, val role: AlertRole = AlertRole.Default, val onClick: () -> Unit)

@Composable
fun IosAlert(
    title: String,
    onDismiss: () -> Unit,
    actions: List<AlertAction>,
    message: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val material = Ios.material
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 520f)) }
        val motion = Modifier.graphicsLayer {
            val s = 1.1f - 0.1f * appear.value
            scaleX = s
            scaleY = s
            alpha = appear.value.coerceIn(0f, 1f)
        }
        if (material) {
            MaterialAlert(title, actions, message, content, motion)
        } else {
            BlurBehind()
            GlassAlert(title, actions, message, content, motion)
        }
    }
}

@Composable
private fun BlurBehind(radius: Dp = 28.dp) {
    val view = LocalView.current
    val px = with(LocalDensity.current) { radius.roundToPx() }
    LaunchedEffect(px) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = px }
        }
    }
}

@Composable
private fun GlassAlert(
    title: String,
    actions: List<AlertAction>,
    message: String?,
    content: (@Composable () -> Unit)?,
    motion: Modifier,
) {
    val c = Ios.colors
    val shape = RoundedCornerShape(34.dp)
    val shade = Color.Black.copy(alpha = 0.35f)
    Column(
        motion
            .padding(horizontal = 24.dp)
            .widthIn(max = 320.dp)
            .shadow(30.dp, shape, ambientColor = shade, spotColor = shade)
            .clip(shape)
            .background(if (c.dark) Color(0xF0262628) else Color(0xF5FAFAFA))
            .border(1.dp, Brush.linearGradient(glassRim(c.dark)), shape)
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = IosType.headline, color = c.label, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = IosType.subheadline, color = c.label, textAlign = TextAlign.Center)
        }
        if (content != null) {
            Spacer(Modifier.height(14.dp))
            content()
        }
        Spacer(Modifier.height(18.dp))
        if (actions.size == 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEach { GlassAlertButton(it, Modifier.weight(1f)) }
            }
        } else {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { GlassAlertButton(it, Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun GlassAlertButton(action: AlertAction, modifier: Modifier) {
    val c = Ios.colors
    val preferred = action.role == AlertRole.Preferred
    Box(
        modifier
            .height(48.dp)
            .iosPress(scaleTo = 0.95f, onClick = action.onClick)
            .clip(CircleShape)
            .background(if (preferred) c.tint else c.fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            action.text,
            style = IosType.body.copy(
                fontWeight = if (preferred || action.role == AlertRole.Cancel) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = when {
                preferred -> Color.White
                action.role == AlertRole.Destructive -> c.red
                else -> c.label
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun MaterialAlert(
    title: String,
    actions: List<AlertAction>,
    message: String?,
    content: (@Composable () -> Unit)?,
    motion: Modifier,
) {
    val c = Ios.colors
    Column(
        motion
            .padding(horizontal = 32.dp)
            .widthIn(min = 280.dp, max = 560.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.label)
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel)
        }
        if (content != null) {
            Spacer(Modifier.height(16.dp))
            content()
        }
        Spacer(Modifier.height(24.dp))
        if (actions.size <= 2) {
            Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { MaterialAlertButton(it) }
            }
        } else {
            Column(Modifier.align(Alignment.End), horizontalAlignment = Alignment.End) {
                actions.forEach { MaterialAlertButton(it) }
            }
        }
    }
}

@Composable
private fun MaterialAlertButton(action: AlertAction) {
    val c = Ios.colors
    Text(
        action.text,
        style = MaterialTheme.typography.labelLarge,
        color = if (action.role == AlertRole.Destructive) c.red else c.tint,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = action.onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
fun IosGlass(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    backdrop: GlassBackdrop? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.liquidGlass(backdrop, shape, GlassStyles.Toast)) { content() }
}

@Composable
fun EdgeHaptics(state: ScrollableState) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state) {
        var back = state.canScrollBackward
        var forward = state.canScrollForward
        snapshotFlow { state.canScrollBackward to state.canScrollForward }.collect { (b, f) ->
            if (state.isScrollInProgress && ((back && !b) || (forward && !f))) {
                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            back = b
            forward = f
        }
    }
}
