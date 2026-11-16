# AUTU-LIO Android screen map

This map is based on the Android source currently on `main`. It is the reference for maintaining the Web preview.

| Android entry / role | Screen or navigation | Android implementation | Web preview |
|---|---|---|---|
| Release entry | Google sign-in / session restore | `AuthenticatedAppHost.kt`, `AuthScreen` | Default page |
| Debug entry | Five-role preview chooser | `MainActivity.kt`, `RolePreviewChooser` | Explicit preview chooser |
| Owner | Overview | `MainActivity.kt`, `OverviewScreen` | Overview |
| Owner | AUTU list, search, filters, draft open/create | `MainActivity.kt`, `ScriptsScreen` | Scripts |
| Owner | Customers | `LiveManagementScreens.kt`, `CustomersAdminScreen` | Customers empty state until API/session |
| Owner | Devices | `LiveManagementScreens.kt`, `DevicesAdminScreen` | Devices empty state until API/session |
| Owner | Collaborator invitation drafts or server collaborators | `CollaboratorsScreen.kt`, `CollaboratorsServerScreen.kt` | Collaborators preview |
| Owner | Shared projects | `SharedProjectScreens.kt`, `ProjectsAdminScreen` | Projects empty state until API/session |
| Owner | Licenses | `LiveManagementScreens.kt`, `LicensesAdminScreen` | Licenses empty state until API/session |
| Owner | Template library | `AdminTemplateLibraryScreen.kt` | Template list |
| Owner | AUTU packages | `LiveManagementScreens.kt`, `AdminPackagesScreen` | Package empty state until API/session |
| Owner | Release overview | `MainActivity.kt`, `MoreScreen` | Release placeholder |
| Owner | Audit logs | `AdminAuditLogsScreen.kt` | Logs empty state until API/session |
| Owner | Settings | `MainActivity.kt`, `MoreScreen` | Settings |
| Owner | Script editor, templates, help, compile, save, prepare run | `ScriptEditorActivity.kt`, `MainActivity.kt`, `ScriptCompiler.kt` | Editor UI; compile/run are deliberately not executed in Web |
| Customer | Home / license status | `MainActivity.kt`, `CustomerHome` | Customer home |
| Customer | Published AUTU package library | `MainActivity.kt`, `CustomerPackagesScreen` | Customer package library; no fabricated package data without live session |
| Customer | Device/license overview, register RSA-3072 device, remove this device | `LiveManagementScreens.kt`, `CustomerDeviceLicenseScreen`, `SecureDeviceIdentityStore.kt` | Device/license live-session empty state |
| Customer | Settings / invitation token / sign-out | `MainActivity.kt`, `SettingsScreen` | Customer settings |
| Developer | Private encrypted local drafts and separate editor | `CollaboratorWorkspace.kt`, `SecureScriptDraftStore.kt`, `ScriptEditorActivity.kt` | Developer workspace/editor |
| Designer | Local UI preview and accent selection | `CollaboratorWorkspace.kt` | Designer workspace |
| Reviewer | Review queue and granted permission summary | `CollaboratorWorkspace.kt` | Reviewer workspace |
| Collaborators | Project list and role-specific project workflow | `SharedProjectScreens.kt`, `CollaboratorProjectsWorkspace` | Shared-project empty state until API/session |

## Important implementation distinctions

- Debug role selection is not authentication. Release starts at the authentication/session host.
- Server-supplied permissions may be narrower than the defaults in `AccessRole.kt`; Web labels are defaults, not live account permissions.
- Android has a script compiler, interpreter engine, Accessibility Service gesture dispatcher and floating runner. Their operation still requires Android permissions and physical-device validation.
- The active Customer navigation uses `CustomerPackagesScreen` and `CustomerDeviceLicenseScreen`; obsolete sample-script and placeholder device composables were removed. Published package download verifies license/device, decrypts the device-wrapped AES key in Android Keystore, checks the source hash, compiles the source and prepares the floating runner.
- Web state is not Android state. It must not claim to persist encrypted app drafts or perform gestures.
