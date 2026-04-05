# アプリケーション概要

発達障害やその他の障害を持つ子供の療育記録を取るためのアプリです。
主に父親と母親が共同で記録・閲覧することを想定しています。

## 主な機能

- **ノート**: 記録の基本単位。一人が複数のノートを管理でき、他のユーザーと「共同編集」が可能です。
- **タイムライン**: メイン画面。ノート内の記録（旧スタンプ）を時系列で表示します。
  - **リアルタイム同期**: `addSnapshotListener` を利用し、複数端末間での即座なデータ同期とオフラインキャッシュの活用を実現しています。
  - **フィルタリング**: 全ての記録種別ごとに表示を絞り込めます。フィルタ状態は月移動時も維持されます。
- **きろく（旧スタンプ）**: 「ねる」「おきる」「メモ」「おでかけ」などのイベントを記録。
  - **詳細入力**: 記録時に日付・時刻・詳細を必ず確認・編集できるフローを採用。
  - **履歴補完**: 「メモ」以外の種別では、過去の入力履歴をアコーディオン方式で参照し、素早く入力できます。
- **タスク管理**: ノートごとに Todo を管理できます。未完了/完了の状態管理と、追加・削除が可能です。
- **ふりかえり**:
  - **睡眠グラフ**: 睡眠・起床記録から睡眠時間を視覚化（月単位）します。
- **インポート・エクスポート**: ノート単位で CSV 形式の書き出し・取り込みが可能です。
- **ログイン**: Firebase Auth (Google ログイン) を使用。非ログイン時は SharedPreferences によるローカル保存となります。

# 設計・構成の知見

## アーキテクチャとパッケージ構成
機能単位でパッケージを分割し、関心の分離を徹底しています。
```
net.eggc.ryoikumemo/
├── ui/                     # UIレイヤー
│   ├── MainViewModel.kt    # アプリ全体の共通状態（ノート、Auth等）を管理
│   ├── AppDestinations.kt  # ナビゲーション定義
│   ├── components/         # 共通部品 (MonthSelector等)
│   └── feature/            # 機能単位のパッケージ
│       ├── timeline/       # タイムライン画面（Screen, MonthPage, Card等）
│       ├── stamp/          # きろく入力・ダイアログ
│       ├── task/           # タスク管理画面
│       ├── graph/          # グラフ表示ロジック
│       ├── note/           # ノート切り替え・管理
│       └── settings/       # 設定・アカウント・規約
├── data/                   # データレイヤー
│   ├── NoteRepository.kt       # ノート・共有管理
│   ├── TimelineRepository.kt   # スタンプ・記録管理
│   ├── TaskRepository.kt       # タスク管理
│   ├── Firestore*.kt           # Firestore実装
│   └── SharedPreferences*.kt   # ローカル保存実装
└── domain/                 # ビジネスロジック (Manager類)
```

## テスト戦略
- **Firestore エミュレータ**: `FirestoreEmulatorRule` を使用し、テスト実行時に自動的にデータをクリアしてエミュレータ上でテストを行います。
  - **起動コマンド**: `firebase emulators:start`
  - **UI確認**: `http://localhost:4000`
- **ユニットテスト**: ロジックの検証は JVM 上で高速に実行します。
  - **実行コマンド**: `./gradlew :app:testDebugUnitTest`
- **インストルメンテーションテスト (Android Test)**: Repository 等、実機/エミュレータが必要なテスト。
  - **実行コマンド**: `./gradlew :app:connectedDebugAndroidTest`
- **カバレッジ (Jacoco)**:
  - **実行コマンド**: `./gradlew :app:jacocoTestReport`
  - レポート場所: `app/build/` 配下に作成します。

## テストのローカル実行

1. Device Manager などを操作して Android のシミュレータを起動します。（または実機を接続します）
2. `firebase emulators:start` により firebase のエミュレータを起動します。
3. `./gradlew :app:jacocoTestReport` により全件テストを実行します。

## UI/UX 方針
- **一貫した操作感**:
  - 月移動には `HorizontalPager` を使用。
  - データ操作時（追加・更新）には `CircularProgressIndicator` 付きのオーバーレイを表示し、通信中のラグによる誤操作を防止します。
- **ダイアログ設計**:
  - `AlertDialog` 内の入力欄は `fillMaxWidth(0.8f)` 程度に抑え、左右の余白を確保することで、キーボードやサジェスト表示時もボタンが見失われないようにします。
  - サジェストは `ExposedDropdownMenu` ではなくアコーディオン方式を採用し、ダイアログ自体の伸縮でボタンの隠れを回避します。

## 実装ガイドライン
- **MainActivity の軽量化**: `MainActivity` はナビゲーション host と、アプリ全体で共有されるダイアログ（スタンプ編集など）の管理に限定。
- **データストリーム**: データの取得には `Flow` を使用し、`collectAsState` で UI に反映します。
- **非推奨 API への対応**:
  - `ClickableText` ではなく `Text` と `AnnotatedString` のリンク機能を使用。
- **エラーハンドリング**: Firestore の権限エラー等でアプリがクラッシュしないよう、リスナー内での安全な空リスト返却を徹底。
