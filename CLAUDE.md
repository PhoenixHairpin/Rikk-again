# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RikkaHub is a native Android LLM chat client that supports switching between different AI providers for conversations.
Built with Jetpack Compose, Kotlin, and follows Material Design 3 principles.

**Version**: 2.1.7 (versionCode 150) | **minSdk**: 26 | **targetSdk**: 36 | **compileSdk**: 36

## Build Commands

```bash
./gradlew assembleDebug            # Build debug APK (applicationId: me.rerere.rikkahub.debug)
./gradlew assembleRelease          # Build release APK (requires signing config in local.properties)
./gradlew bundleRelease            # Build release AAB for Play Store
./gradlew buildAll                 # Build both release APK and AAB
./gradlew test                     # Run JVM unit tests
./gradlew :app:testDebugUnitTest   # Run app module tests only
./gradlew :ai:testDebugUnitTest    # Run AI module tests only
./gradlew connectedDebugAndroidTest # Run instrumented tests on device/emulator
./gradlew lint                     # Run Android Lint
./gradlew :app:compileDebugKotlin  # Compile Kotlin only (faster feedback)
```

**Prerequisites**:
- A `google-services.json` file in the `app/` folder (for Firebase)
- For release builds: `local.properties` must contain `storeFile`, `storePassword`, `keyAlias`, `keyPassword`
- JDK 17 required (compileOptions and Kotlin JVM target set to 17)
- Gradle JVM args: `-Xmx2g`

## Architecture Overview

### Module Structure

- **app**: Main application module with UI, ViewModels, core logic, and root support (libsu)
- **ai**: AI SDK abstraction layer for different providers (OpenAI, Google, Anthropic)
- **common**: Common utilities and extensions (HTTP helpers, Android extensions)
- **document**: Document parsing module for handling PDF, DOCX, and PPTX files
- **highlight**: Code syntax highlighting using Prism.js via QuickJS engine
- **search**: Search functionality SDK (Exa, Tavily, Zhipu)
- **tts**: Text-to-speech implementation for different providers
- **web**: Embedded Ktor web server that hosts the React web-ui frontend
- **web-ui**: React frontend project (built from source, served by Ktor)

### Key Technologies

- **Jetpack Compose**: Modern UI toolkit
- **Navigation 3**: App navigation (using `NavDisplay` and `NavKey`, not traditional NavController)
- **Koin**: Dependency injection (appModule, dataSourceModule, repositoryModule, viewModelModule)
- **Room**: Database ORM with SQLite-android (requery) and jieba full-text search
- **DataStore**: Preferences storage (SettingsStore with JSON serialization)
- **OkHttp**: HTTP client with SSE support for streaming
- **Pebble**: Template engine for prompt templates
- **Coil 3**: Image loading with OkHttp network layer
- **Kotlinx Serialization**: JSON handling
- **Firebase**: Analytics, Crashlytics, Remote Config (optional, needs google-services.json)
- **libsu**: Root shell execution support

### Core Packages (app module)

- `data/`: Data layer with repositories, database entities, and API clients
  - `data/ai/`: AI generation (GenerationHandler, ProviderManager, transformers)
  - `data/db/`: Room database (AppDatabase, DAOs, entities, migrations, FTS)
  - `data/datastore/`: Settings management (SettingsStore, JSON serialization)
  - `data/repository/`: Repositories (ConversationRepository, MemoryRepository, etc.)
  - `data/files/`: File management (FilesManager, SkillManager)
  - `data/sync/`: Sync providers (S3Sync, WebDavSync)
  - `data/export/`: Export/import functionality
  - `data/root/`: Root shell support (RootManager, LibsuShellRuntime, RootTerminalSessionManager)
  - `data/ai/mcp/`: MCP (Model Context Protocol) support
  - `data/ai/tools/`: Local AI tools (JavaScript engine, clipboard, TTS, shell_exec)
  - `data/ai/transformers/`: Message pipeline transformers
- `ui/pages/`: Screen implementations and ViewModels
- `ui/components/`: Reusable UI components
- `ui/composables/`: Shared composable UI elements
- `di/`: Koin DI modules (AppModule, DataSourceModule, RepositoryModule, ViewModelModule)
- `service/`: Background services (ChatService, WebServerService)
- `utils/`: Utility functions and extensions
- `web/`: Ktor web server routes and handlers

### Services

- **ChatService**: Central orchestrator for chat operations. Handles message sending, regeneration, tool approval, translation, title/suggestion generation. Uses `ConversationSession` for per-conversation state management with reference counting.
- **WebServerService**: Starts a Ktor HTTP server on the device, serving the React web-ui and exposing REST API endpoints for remote chat control.
- **AppScope**: Application-level CoroutineScope used for background operations.

### Concepts

- **Assistant**: An assistant configuration with system prompts, model parameters, and conversation isolation. Each assistant maintains its own settings including temperature, context size, custom headers, tools, memory options, regex transformations, and prompt injections (mode/lorebook). Assistants provide isolated chat environments with specific behaviors and capabilities. (app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt)

- **Conversation**: A persistent conversation thread between the user and an assistant. Each conversation maintains a list of MessageNodes in a tree structure to support message branching, along with metadata like title, creation time, and pin status. Conversations can be truncated at a specific index and maintain chat suggestions. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **UIMessage**: A platform-agnostic message abstraction that encapsulates chat messages with different types of content parts (text, images, documents, reasoning, tool calls/results, etc.). Each message has a role (USER, ASSISTANT, SYSTEM, TOOL), creation timestamp, model ID, token usage information, and optional annotations. UIMessages support streaming updates through chunk merging. (ai/src/main/java/me/rerere/ai/ui/Message.kt)

- **MessageNode**: A container holding one or more UIMessages to implement message branching functionality. Each node maintains a list of alternative messages and tracks which message is currently selected (selectIndex). This enables users to regenerate responses and switch between different conversation branches, creating a tree-like conversation structure. (app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt)

- **Message Transformer**: A pipeline mechanism for transforming messages before sending to AI providers (InputMessageTransformer) or after receiving responses (OutputMessageTransformer). Transformers can modify message content, add metadata, apply templates, handle special tags, convert formats, and perform OCR. Common transformers include:
  - TemplateTransformer: Apply Pebble templates to user messages with variables like time/date
  - ThinkTagTransformer: Extract `<think>` tags and convert to reasoning parts
  - RegexOutputTransformer: Apply regex replacements to assistant responses
  - DocumentAsPromptTransformer: Convert document attachments to text prompts
  - Base64ImageToLocalFileTransformer: Convert base64 images to local file references
  - OcrTransformer: Perform OCR on images to extract text

  Output transformers support `visualTransform()` for UI display during streaming and `onGenerationFinish()` for final processing after generation completes.
  (app/src/main/java/me/rerere/rikkahub/data/ai/transformers/Transformer.kt)

## Development Guidelines

### UI Development

- Follow Material Design 3 principles
- Use existing UI components from `ui/components/`
- Reference `SettingProviderPage.kt` for page layout patterns
- Use `FormItem` for consistent form layouts
- Implement proper state management with ViewModels
- Use `Lucide.XXX` for icons, and import `import com.composables.icons.lucide.XXX` for each icon
- Use `LocalToaster.current` for toast messages
- Uses Navigation 3 (`NavDisplay` + `NavKey`) — NOT traditional `NavController`
- Uses `rememberViewModelStoreNavEntryDecorator()` for ViewModel scoping in navigation entries
- Image loading: use `setSingletonImageLoaderFactory` with Coil 3 + OkHttp

### Internationalization

- String resources located in `app/src/main/res/values-*/strings.xml`
- Use `stringResource(R.string.key_name)` in Compose
- Page-specific strings should use page prefix (e.g., `setting_page_`)
- If the user does not explicitly request localization, prioritize implementing functionality without considering
  localization. (e.g `Text("Hello world")`)
- If the user explicitly requests localization, all languages should be supported.
- English(en) is the default language. Chinese(zh), Japanese(ja), and Traditional Chinese(zh-rTW), Korean(ko-rKR) are supported.
- When localization is needed, use the `locale-tui-localization` skill for managing string resources.

### Database

- Room database with migration support
- Schema files in `app/schemas/`
- Uses requery SQLite-Android wrapper (not built-in SQLite) with jieba full-text search
- Current database version tracked in `AppDatabase.kt`
- Migrations: 6→7, 11→12, 13→14, 14→15, 15→16, plus auto-migrations for 10→11, 12→13, 16→17
- FTS uses `MessageFtsManager` with simple tokenization (jieba for Chinese)
- DAOs use Paging3 for paginated queries

### AI Provider Integration

- New providers go in `ai/src/main/java/me/rerere/ai/provider/providers/`
- Extend base `Provider` class
- Implement required API methods following existing patterns
- Support for streaming responses via SSE

### Root Support

- Uses libsu library (`com.topjohnwu.superuser.Shell`)
- Root tools are opt-in: `shell_exec` is only available when `settings.rootEnabled == true` AND root status is GRANTED
- `RootManager` detects Magisk/KernelSU/APatch automatically
- `RootTerminalSessionManager` provides an interactive root shell session

### Web UI

- The `web-ui/` directory contains a React frontend project
- Built output is served by the Ktor web server embedded in the app
- Use `bun install` and `bun run build` in `web-ui/` to rebuild the frontend

### Notification Channels

- `chat_completed`: Generation completion notifications
- `chat_live_update`: Live generation status updates
- `web_server`: Web server status notifications

### Important Notes

- The `buildAll` task depends on both `assembleRelease` and `bundleRelease`
- ABI splits are configured for `arm64-v8a` and `x86_64` with a universal APK
- ProGuard/R8 is enabled for release builds (see `proguard-rules.pro`)
- Compose compiler stability config is in `compose_compiler_config.conf`
- Many Compose APIs require opt-in annotations (see `build.gradle.kts` lines 118-130)
