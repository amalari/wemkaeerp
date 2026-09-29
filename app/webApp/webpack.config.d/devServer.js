if (config.output) {
    config.output.publicPath = '/';
}
if (config.devServer) {
    // Repo B (platform) berjalan berdampingan dengan repo A (konveksi, 3000/8080): default 3001 → API 8081.
    config.devServer.port = Number(process.env.WEMADE_WEB_PORT || 3001);
    config.devServer.historyApiFallback = true;
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:' + (process.env.WEMADE_API_PORT || 8081),
            changeOrigin: true
        }
    ];
}
