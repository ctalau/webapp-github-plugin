var gulp = require('gulp');

var concat = require('gulp-concat');
var uglify = require('gulp-uglify');
var rename = require('gulp-rename');

gulp.task('prepare-package', function() {
    return gulp.src(['web/github.js', 'web/plugin.js'])
        .pipe(concat('plugin.js'))
        .pipe(uglify())
        .pipe(gulp.dest('target/'));
});
gulp.task('default', ['prepare-package']);
