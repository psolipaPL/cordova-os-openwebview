var exec = require('cordova/exec');

var OpenWebview = {
    open: function (params, success, error) {
        exec(
            function (jsonString) {
                var payload;
                try {
                    payload = JSON.parse(jsonString);
                } catch (e) {
                    return;
                }
                success && success(payload);
            },
            function (err) {
                error && error(err);
            },
            "OpenWebview",
            "open",
            [ params || {} ]
        );
    },

    close: function (success, error) {
        exec(
            function (jsonString) {
                var payload;
                try {
                    payload = JSON.parse(jsonString);
                } catch (e) {
                    return;
                }
                success && success(payload);
            },
            function (err) {
                error && error(err);
            },
            "OpenWebview",
            "close",
            []
        );
    },

    EVENT_TYPE: {
        SUCCESS: 1,
        FINISHED: 2,
        PAGE_LOADED: 3,
        NAVIGATION_COMPLETED: 4
    }
};

module.exports = OpenWebview;
