$(document).ready(function() {
    $('ul.nav > li > a[href="' + document.location.pathname + '"]').parent().addClass('active');
});
