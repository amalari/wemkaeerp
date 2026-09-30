package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.prefill.LeadDraftFields
import com.eventverse.app.presentation.crm.CrmUiEvent
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftUiEffect
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftUiEvent
import com.eventverse.app.presentation.crm.leaddraft.LeadDraftViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog pembuatan Lead baru dengan gaya Claymorphism Neo-Brutalis:
 * - Brand / Perusahaan: Opsional
 * - Nomor Handphone: Validasi format nomor Indonesia (10–13 digit lokal 08... atau +628...)
 * - Email: Opsional
 * - Field kustom tenant (Teks/Angka/Pilihan) dirender dari skema
 * - "Isi dengan AI" (TRD-HELP-002): draf mengisi field, **user** yang menekan Simpan
 */
@Composable
fun CreateLeadDialog(
    initialStage: LeadStage = LeadStage.NEW_LEAD,
    customSchema: List<LeadFieldDescriptor> = emptyList(),
    onDismiss: () -> Unit,
    onCreate: (CrmUiEvent.CreateLead) -> Unit
) {
    val form = remember { LeadFormState(initialStage) }
    val draftViewModel = remember { LeadDraftViewModel() }
    val draftState by draftViewModel.uiState.collectAsState()
    LaunchedEffect(draftViewModel) {
        draftViewModel.onEvent(LeadDraftUiEvent.Load)
        draftViewModel.effects.collect { if (it is LeadDraftUiEffect.Apply) form.applyDraft(it.draft) }
    }
    val canSubmit = form.canSubmit(customSchema)
    fun label(base: String, key: String) = base + aiSuffix(form.isAi(key))

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.width(460.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tambah Lead Baru", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                    StagePill(label = "Inquiry", selected = form.stage == LeadStage.NEW_LEAD, onClick = { form.stage = LeadStage.NEW_LEAD })
                    StagePill(label = "Follow Up", selected = form.stage == LeadStage.FOLLOW_UP, onClick = { form.stage = LeadStage.FOLLOW_UP })
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(top = ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                LeadAiDraftSection(
                    state = draftState,
                    fieldLabel = { key -> CORE_LABELS[key] ?: customSchema.firstOrNull { it.fieldId == key }?.label ?: key },
                    onTextChange = { draftViewModel.onEvent(LeadDraftUiEvent.UpdateText(it)) },
                    onExtract = { draftViewModel.onEvent(LeadDraftUiEvent.Extract) },
                    onEnable = { draftViewModel.onEvent(LeadDraftUiEvent.Enable) }
                )
                ClayTextField(
                    value = form.brandName, onValueChange = { form.update(LeadDraftFields.BRAND_NAME, it) },
                    label = label("Nama Brand/Perusahaan (Opsional)", LeadDraftFields.BRAND_NAME),
                    placeholder = "Misal: PT Sinar Jaya / Brand XYZ", modifier = Modifier.fillMaxWidth()
                )
                ClayTextField(
                    value = form.contactPerson, onValueChange = { form.update(LeadDraftFields.CONTACT_PERSON, it) },
                    label = label("Nama Kontak", LeadDraftFields.CONTACT_PERSON),
                    placeholder = "Misal: Budi / Bu Dewi", modifier = Modifier.fillMaxWidth()
                )
                FieldWithError(
                    error = "Nomor handphone Indonesia: 10–13 digit (diawali 08, 628, atau +628)".takeIf { !form.isPhoneValid }
                ) {
                    ClayTextField(
                        value = form.phone, onValueChange = { form.update(LeadDraftFields.WHATSAPP, it) },
                        label = label("Nomor Handphone", LeadDraftFields.WHATSAPP),
                        placeholder = "Misal: 081234567890 atau +62812...",
                        focusColor = if (!form.isPhoneValid) WeMadeColors.Error else WeMadeColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                FieldWithError(error = "Format email belum sesuai (contoh: sales@brand.co.id)".takeIf { !form.isEmailValid }) {
                    ClayTextField(
                        value = form.email, onValueChange = { form.update(LeadDraftFields.EMAIL, it) },
                        label = label("Email (Opsional)", LeadDraftFields.EMAIL),
                        placeholder = "nama@perusahaan.com",
                        focusColor = if (!form.isEmailValid) WeMadeColors.Error else WeMadeColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayTextField(
                        value = form.productCategory, onValueChange = { form.update(LeadDraftFields.PRODUCT_CATEGORY, it) },
                        label = label("Kategori Produk", LeadDraftFields.PRODUCT_CATEGORY),
                        placeholder = "Polo, Kemeja, Hoodie...", modifier = Modifier.weight(1.3f)
                    )
                    ClayTextField(
                        value = form.pcs, onValueChange = { form.update(LeadDraftFields.ESTIMATED_PCS, it) },
                        label = label("Kuantiti (Pcs)", LeadDraftFields.ESTIMATED_PCS),
                        placeholder = "Misal: 500", modifier = Modifier.weight(0.7f)
                    )
                }
                LeadCustomFieldInputs(schema = customSchema, form = form)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xl),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayButton(text = "Batal", onClick = onDismiss, style = ClayButtonStyle.Secondary, modifier = Modifier.weight(1f))
                ClayButton(
                    text = "Simpan",
                    onClick = {
                        if (canSubmit) onCreate(
                            CrmUiEvent.CreateLead(
                                brandName = form.brandName.trim(),
                                contactPerson = form.contactPerson.trim(),
                                phoneNumber = form.phone.trim(),
                                email = form.email.trim(),
                                stage = form.stage,
                                productCategory = form.productCategory.trim(),
                                estimatedPcs = form.pcs.toIntOrNull(),
                                customValues = form.customValues(customSchema),
                                createdVia = form.createdVia
                            )
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FieldWithError(error: String?, field: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        field()
        if (error != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.Error, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private val CORE_LABELS = mapOf(
    LeadDraftFields.BRAND_NAME to "Nama Brand", LeadDraftFields.CONTACT_PERSON to "Nama Kontak",
    LeadDraftFields.WHATSAPP to "Nomor Handphone", LeadDraftFields.EMAIL to "Email",
    LeadDraftFields.PRODUCT_CATEGORY to "Kategori Produk", LeadDraftFields.ESTIMATED_PCS to "Kuantiti",
)

@Composable
private fun StagePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .claySurface(
                shape = ClayShapes.Pill,
                background = if (selected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                outline = if (selected) WeMadeColors.Primary else WeMadeColors.OutlineSoft,
                offset = if (selected) ClayOffset.Flat else ClayOffset.Small,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Sm, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
        )
    }
}
