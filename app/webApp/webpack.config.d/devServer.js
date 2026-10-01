if (config.output) {
    config.output.publicPath = '/';
}
if (config.devServer) {
    // Repo B (platform) berjalan berdampingan dengan repo A (konveksi, 3000/8080): default 3001 → API 8081.
    config.devServer.port = Number(process.env.WEMADE_WEB_PORT || 3001);
    config.devServer.historyApiFallback = true;
    // Uji permukaan host (app./<slug>.) lokal lewat *.lvh.me (wildcard DNS → 127.0.0.1).
    // Daftar eksplisit, bukan 'all': host lain tetap ditolak (proteksi DNS rebinding).
    // Hanya dev — produksi disajikan Caddy, file ini tidak dibaca.
    config.devServer.allowedHosts = ['localhost', '127.0.0.1', '.lvh.me'];
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:' + (process.env.WEMADE_API_PORT || 8081),
            changeOrigin: true
        }
    ];
}
