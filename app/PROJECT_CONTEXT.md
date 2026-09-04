# Project Context: DoffiSecure

## Project Root
`E:\Android studio Projects\app`

## MVP Status
- **Core Features**: Create, Read, Delete, Search, and Update (in progress) passwords.
- **Done**:
    - Refactored `IPasswordRepository`.
    - Dependency injection with Koin configured (`AppModule`).
    - `UpdatePasswordUseCase` implementation.
    - Basic UI for listing, adding, deleting, and searching passwords.
    - Edit functionality partially implemented (ViewModel + Dialog logic).
- **In Progress**:
    - Finalizing UI in `PasswordScreen.kt` to ensure smooth Edit/Add/Delete flows.

## Planned Project Structure
```text
src/main/java/com/doffi4/doffisecure/
├── data/
│   ├── local/
│   │   ├── dao/ (PasswordDao)
│   │   ├── database/ (AppDatabase)
│   │   └── entities/ (PasswordDatabaseEntity)
│   └── repository/ (PasswordRepositoryImpl)
├── di/ (AppModule - Koin)
├── domain/
│   ├── model/ (PasswordEntity)
│   ├── repository/ (IPasswordRepository)
│   └── usecase/ (Add, Delete, Get, Search, Update Password UseCases)
├── ui/
│   ├── password/ (PasswordScreen, PasswordViewModel, PasswordUiState)
│   └── theme/ (Color, Theme, Type)
├── DoffiSecureApplication.kt
└── MainActivity.kt
```

## Future Tasks
1.  Complete `PasswordScreen.kt` UI integration.
2.  Implement "Copy to Clipboard" functionality for passwords.
3.  End-to-end testing of the CRUD lifecycle.

## Architectural Decisions
- **Clean Architecture**: Separation of concerns into Data, Domain, and UI layers.
- **MVVM Pattern**: Using ViewModel to manage UI state and interact with UseCases.
    - **Option A**: Focused on a robust, testable, and scalable structure using standard Android modern libraries (Room, Compose, Koin).
