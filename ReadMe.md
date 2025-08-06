GitHub Actions : https://docs.github.com/zh/actions/get-started/quickstart

1. 创建目录
    .github/workflows
2. 创建工作流文件: 
    github-actions.yml
3. 设置代理
   git config --global http.proxy 'http://127.0.0.1:7897'   
   git config --global https.proxy 'http://127.0.0.1:7897'
   # 取消代理
   git config --global --unset http.proxy
   git config --global --unset https.proxy