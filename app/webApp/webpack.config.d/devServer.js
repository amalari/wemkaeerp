if (config.output) {
    config.output.publicPath = '/';
}
if (config.devServer) {
    config.devServer.port = 3000;
    config.devServer.historyApiFallback = true;
    config.devServer.proxy = [
        {
            context: ['/api'],
            target: 'http://localhost:8080',
            changeOrigin: true
        }
    ];
}
