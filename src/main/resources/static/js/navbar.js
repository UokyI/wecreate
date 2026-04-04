/**
 * WeCreate 顶部导航栏 JavaScript
 * 用于所有页面的统一导航栏功能
 */

// 切换移动端菜单
function toggleNavbarMenu() {
    const menu = document.getElementById('navbarMenu');
    if (menu) {
        menu.classList.toggle('show');
    }
}

// 初始化导航栏
function initNavbar() {
    // 设置当前活动菜单项
    setActiveMenuItem();
    
    // 添加键盘快捷键支持
    addKeyboardShortcuts();
}

// 设置当前活动菜单项
function setActiveMenuItem() {
    const currentPath = window.location.pathname;
    const menuItems = document.querySelectorAll('.navbar-menu-item');
    
    menuItems.forEach(item => {
        const href = item.getAttribute('href');
        if (href === currentPath) {
            item.classList.add('active');
        } else {
            item.classList.remove('active');
        }
    });
}

// 添加键盘快捷键
function addKeyboardShortcuts() {
    document.addEventListener('keydown', function(event) {
        // F2: 返回首页
        if (event.key === 'F2') {
            event.preventDefault();
            window.location.href = '/list.html';
        }
        // F8: 切换暗色主题
        if (event.key === 'F8') {
            event.preventDefault();
            toggleDarkTheme();
        }
    });
}

// 切换暗色主题
function toggleDarkTheme() {
    document.body.classList.toggle('dark-theme');
    const isDarkTheme = document.body.classList.contains('dark-theme');
    localStorage.setItem('theme', isDarkTheme ? 'dark' : 'light');
    updateThemeButtonIcon(isDarkTheme);
}

// 更新主题按钮图标
function updateThemeButtonIcon(isDarkTheme) {
    const themeBtn = document.getElementById('themeToggleBtn');
    if (themeBtn) {
        themeBtn.innerHTML = isDarkTheme 
            ? '<span class="theme-icon">☀️</span>' 
            : '<span class="theme-icon">🌙</span>';
    }
}

// 恢复主题设置
function restoreTheme() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark') {
        document.body.classList.add('dark-theme');
        updateThemeButtonIcon(true);
    } else {
        updateThemeButtonIcon(false);
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    initNavbar();
    restoreTheme();
});
