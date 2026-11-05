package com.metashield.app.ui.navigation

enum class Screen(val route: String, val title: String) {
    Splash("splash", "MetaShield"),
    HomeDashboard("home_dashboard", "MetaShield"),
    ImageSelection("image_selection", "Select Image"),
    ImagePreview("image_preview", "Image Preview"),
    MetadataScanner("metadata_scanner", "Metadata Scanner"),
    C2paInspection("c2pa_inspection", "C2PA Content Credentials"),
    MetadataCleaning("metadata_cleaning", "Metadata Cleaning"),
    Processing("processing", "Sanitizing Image"),
    BeforeAfterResults("before_after_results", "Verification Results"),
    ExportSave("export_save", "Export & Save"),
    Settings("settings", "Settings"),
    About("about", "About MetaShield")
}
