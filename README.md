# 開発環境のセットアップ

1. android studio をダウンロードしてインストールする
2. device manager で create virtual device を選択し適当な仮想デバイスをインストールする API バージョンは36を選択
3. git clone でソースコードをチェックアウトする
4. firebase のプロジェクトページから google-services.json をダウンロードし app/ 配下に置く
5. ./gradlew signingReport を実行してフィンガープリントをコピー
6. firebase プロジェクト設定＞マイアプリ＞SHA1のフィンガープリント追加
7. 仮想デバイスの設定アプリ(Settings)を開き、Google アカウントにログイン
8. gradle の同期をとってから仮想デバイスにアプリをインストールして実行
