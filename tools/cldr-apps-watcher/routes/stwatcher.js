/**
 * Survey Tool Watcher
 * @author srl
 */

var os = require('os');
var ping = require("ping");
var    url = require("url");
var    request = require("request");
var    orm = require("orm");
var    CONFIG = require("config").SurveyWatcher;
var VERBOSE = CONFIG.watcher.verbose || false;
// local data
var hosts = {};
var uhosts = {};
var servers = {};
var hostname = os.hostname();

console.log("Setup stwatcher.. verbose="+VERBOSE);

for ( var k in CONFIG.servers ) {
    if(CONFIG.servers[k].skip) {
        continue;
    }
    if(CONFIG.servers[k].url.substr(CONFIG.servers[k].url.length-1)!='/') {
        CONFIG.servers[k].url = CONFIG.servers[k].url + '/'; // always end with /
    }
    var theurl  = url.parse(CONFIG.servers[k].url);
    console.log("Considering " + k + " URL " + CONFIG.servers[k].url);
    if(!hosts[theurl.hostname]) {
        hosts[theurl.hostname] = { stealth: false, servers: {} };
    }
    servers[k] = { host: theurl.hostname };
    hosts[theurl.hostname].servers[k] = { url: CONFIG.servers[k].url }; // only URL. no other leak.
    if(CONFIG.servers[k].stealth) {
        hosts[theurl.hostname].stealth = true;
    }
}

function st_notify(opts) {
    if(VERBOSE) console.log("Event: " + JSON.stringify(opts));

    var notifyList = null;

    if(opts.event == 'boot') {
        if(CONFIG.notify.boot) {
            notifyList = CONFIG.notify.boot;
        } else {
            notifyList = null;
        }
    } else {
        notifyInfo = CONFIG.servers[opts.server];
        if(notifyInfo && !notifyInfo.disabled && notifyInfo.events) {
            notifyList = notifyInfo.events[opts.event];
        } else {
	    if(VERBOSE) console.log("Not notifying " + opts.server + " event " + opts.event + " - disabled or not configured.");
            notifyList = null;
        }
    }

    if(notifyList != null) {
        if(!Array.isArray(notifyList)) {
            notifyList = [notifyList];
        }
        
        for(var k in notifyList) {
            var notifyType = notifyList[k];
	    if(VERBOSE) console.log("Considering notify kind " + notifyType);
            var notify = CONFIG.notify[notifyType];
            // TODO merge notifyList[k] with CONFIG.notify[k]
            
            if(notify.disabled) {
		if(VERBOSE) console.log("Skipping notify as disabled: " + notifyType);
                continue;
	    }
            
            if(notify.kind == "email") {
		if(VERBOSE) console.log("Sending email notification " + notifyType);;
                var nodemailer = require("nodemailer");
                var smtpTransport = nodemailer.createTransport(notify.mail_kind, 
                                                               notify.mail_opts);
                
                var outopts = {
                    from: notify.from,
                    to: notify.to,
                    subject: notify.sub + ' ' + opts.message,
                    text: 'Event: ' + opts.event + '\r\n' + 
                        ((opts.server)?('Server: ' + opts.server+'\n'):'') +
                        ('Since: ' + opts.since) + '\n' +  // TODO  timezone
                        ('Message: ' + opts.message) + '\n' +
                        ('Details: ' + opts.details) + '\n' +
                        (notify.footer) + '\n'
                };

                smtpTransport.sendMail(outopts, function(error, response) {
                    if(error) {
                        console.log(error);
                    } else {
                        console.log("Sent: " + notifyType + " - " +  response.message + " " + JSON.stringify(opts));
                    }
                    smtpTransport.close(); // no more messages
                });
	    } else if (notify.kind == "xmpp") {
		var mynotify = notify;
		if(!notify.xmpp) {
		    notify.queue = [];
		    notify.send = function(a,b) { notify.queue.push([a,b]); console.log('Queueing xmpp, len='+notify.queue.length); }

		    var myxmpp = notify.xmpp = require('simple-xmpp');
		    if(VERBOSE) { console.log("Connecting with simple-xmpp as " + notify.login.jid + " @ "+
					      notify.login.host+":"+notify.login.port); }
		    notify.xmpp.connect( notify.login );

		    notify.xmpp.on('online', function() {
			mynotify.send = myxmpp.send; // direct, now

			console.log('XMPP connected! processing queue of ' + mynotify.queue.length);

			for(var q in mynotify.queue) {
			    if(VERBOSE) console.log('Processing deferred xmpp send to ' + mynotify.queue[q][0]);
			    myxmpp.send(mynotify.queue[q][0], mynotify.queue[q][1]);
			}
			mynotify.queue = null;
		    });

		    notify.xmpp.on('subscribe', function(from) {
			console.log('Subscribed to XMPP: ' + from);
			myxmpp.acceptSubscription(from);
			myxmpp.subscribe(from);
			myxmpp.send(from, 'Hey! Im just a robot. Send me "help" for help.');
		    });

		    notify.xmpp.on('chat', function(from, message) {
			console.log('Message from : ' + from + '  - ' + message);
			var word = message.split(' ')[0];
			var reply = null;
			if(word==='help') {
			    reply = 'Commands: "help", "status"';
			} else if(word==='status') {
			    reply = 'Status:\n';
			    for(var svr in servers) {
				var svre = servers[svr];
				reply += svr + ': ';
				if(svre.lastKnownStatus) {
				    reply +=  (svre.lastKnownStatus.up?'UP':'DOWN') + ' as of ' + svre.lastKnownStatus.when + '\n';
				    if(svre.latestStatus && svre.latestStatus.update.busted!==null) {
					reply += ' '  +svre.latestStatus.update.statusCode + ':busted=' + svre.latestStatus.update.busted + '\n';
				    }
				} else {
				    reply += 'status not known (ask me later)\n';
				}
			    }
			    
			} else {
			    reply = 'Didn\'t get that.. send me "help" for help.';
			}
			myxmpp.send(from, reply);
		    });

		    notify.xmpp.on('error', function(err) {
			console.error(err);
		    });
		}
		var xmpp = notify.xmpp;
		console.log('sending to ' + notify.buddies);
		for(budn in notify.buddies) {
		    var bud = notify.buddies[budn];
		    if(VERBOSE) { console.log("Sending to " + bud); }
		    notify.send(bud, 'Event: ' + opts.event + '\r\n' +
                              ((opts.server)?('Server: ' + opts.server+'\n'):'') +
                              ('Since: ' + opts.since) + '\n' +
                              ('Message: ' + opts.message) + '\n' +
                              ('Details: ' + opts.details) + '\n');
		}
	    } else if(notify.kind === 'twilio') {
		var mynotify = notify;
		if(!mynotify.twilio) {
		    mynotify.twilio = require('twilio')(notify.account.account_sid,notify.account.auth_token);
		    console.log('Twilio acct set up to send from ' + notify.from);
		}
            } else {
                console.log("Unknown notify kind " + notify.kind);
            }
        }
    }
    
}

// just to test notify
st_notify({ event: "boot",
            message: "Watcher started@"+hostname,
            details: "STWatcher has started",
            since: new Date()
          });


if(!CONFIG.watcher.dbpath) {
    throw("No config/SurveyWatcher.watcher.dbpath - needs to be of the form mysql://username:password@host/database");
}

exports.latest = function(req,res) {
    exports._latest(req,res);
}

exports._latest = function(req,res) {
    res.send("Not setup yet");
}


exports.history = function(req,res) {
    exports._history(req,res);
}

exports._history = function(req,res) {
    res.send("Not setup yet");
}

orm.connect(CONFIG.watcher.dbpath, function (orm_err, db) {
    if(orm_err) {
        console.log("Error with database: " + orm_err.toString());
        throw orm_err;
    }

    // setup DB stuff
    var HostStatus = db.define("hoststatus", {
        host      : String,
        when      : Date,
        alive     : Boolean,
        ns        : Number
    }, {
        methods: {
        },
        validations: {
        }
    });

    var FetchStatus = db.define("fetchstatus", {
        server    : String,
        when      : Date,
        ns        : Number,
        probation : Boolean,
        isSetup   : Boolean,
        isBusted  : Boolean,
        users     : Number,
        guests    : Number,
        pages     : Number,
        dbused    : Number,
        mem       : String,
        load      : String,
        info      : String,
        busted    : String,
        uptime    : String,
        stamp     : Date, // boot time, relatively
        statusCode : Number 
    }, {
        methods: {
        },
        validations: {
        }
    });
    
    db.sync(function (err) {
        !err && console.log("db synced!");
    
    
    function postHostStatus(anUpdate) {
        hosts[anUpdate.host].latestPing = anUpdate;
        HostStatus.create( [anUpdate], 
                           function (err, items) {
                               if(err) throw(err);
                           });
    }
 
    function postFetchStatus(anUpdate, json) {
        servers[anUpdate.server].latestStatus = { update: anUpdate, json: json };
        var up = (anUpdate.isBusted==false && anUpdate.statusCode == 200);
	if(VERBOSE) console.log('Posting ' + anUpdate.server + ' as ' + up);
        var newStatus = { when: anUpdate.when, id: -1, up: up };
        if(servers[anUpdate.server].lastKnownStatus) {
            if(up != servers[anUpdate.server].lastKnownStatus.up) {
                console.log(anUpdate.when + ": server " + anUpdate.server + " change from " + servers[anUpdate.server].lastKnownStatus.up + " to " + up);
                
                if(up==false) { 
                    newStatus.probation = true;
                    if(VERBOSE) { console.log("Rechecking in " + exports._probsec + " sec on probation") };
                    exports._probtimeout = setTimeout(exports.poll, exports._probsec * 1000);
                } else {
                    if(servers[anUpdate.server].lastKnownStatus.probation) {
                        console.log("Server " + anUpdate.server + " no longer on probation");
                    } else {
                        st_notify({ event: "up",
                                    server: anUpdate.server,
                                    message: ("SurveyTool " + anUpdate.server + " UP "),
                                    since: anUpdate.when
                                  });
                    }
                }
            }
            if( up == false && servers[anUpdate.server].lastKnownStatus.probation ) {
                console.log(anUpdate.when + ": server " + anUpdate.server + " - PROBATION EXPIRED!!! YELL AND SCREAM!");
                st_notify({ event: "down",
                            server: anUpdate.server,
                            message: ("SurveyTool " + anUpdate.server + " DOWN"),
                            details: anUpdate.busted,
                            since: servers[anUpdate.server].lastKnownStatus.when
                          });
            }
        }
        FetchStatus.create( [anUpdate], 
            function (err, items) {
                if(err) throw(err);
                newStatus.id = items[0].id;
                servers[anUpdate.server].lastKnownStatus = newStatus;
            });
    }
    

exports.poll = function() {
    var now = new Date();

    if(VERBOSE) console.log("polling at " + now);

    var latestUpdate = { time: now, hosts: [], servers: {}  };


    for(var k in hosts) {
        if ( !hosts[k].stealth ) {
            //console.log('<<');
            var startTime = process.hrtime();
            try {
                //console.log("Pinging: " + k);
                ping.sys.probe(k,
                               (function(k){
                                   return function(isAlive) {
                                       var delta = process.hrtime(startTime);
                                       var ns = (delta[0] * 1e9) + delta[1];
                                       //console.log('Ping ' + ns + ' = ' + isAlive);
                                       var anUpdate = { host: k, when: now,  alive: isAlive, ns: ns };
                                       postHostStatus(anUpdate);
                                   };
                               })(k));
            } catch (e) {
                console.log('ping Fail: ' + e);
                var delta = process.hrtime(startTime);
                var ns = (delta[0] * 1e9) + delta[1];
                postHostStatus({ host: k, when: now, alive: false, ns: ns });
            }
        }

        for(var j in hosts[k].servers) {
            var server = hosts[k].servers[j];

            var theurl  = url.parse(server.url);
            var statusurl = url.format(url.resolve(theurl, 'SurveyAjax?what=status'));

            if(VERBOSE) console.log("* " + statusurl);
            var startTime = process.hrtime();
            request.get({url: statusurl}, (function (server,j){
                return function(error, response, body) {
                    var delta = process.hrtime(startTime);
                    var ns = delta[0] * 1e9 + delta[1];
                    
//                    console.log("Got: " + body);
//                    if(body!=null) {
//                        body = JSON.parse(body);
//                        console.log("Got: " + body + ", error: " + error);
//                    }
                    
                    var statusCode = 0;
                    if(error != null) { 
                        body = null;
                        statusCode=-1;
                    } else {
                        statusCode = response.statusCode; // may or may not be 200
                    }                        

                    var record = { server: j,
                                      when: now,
                                      ns: ns, 
                                      statusCode: statusCode };

                    if(body!=null) {
                        try {
                            body = JSON.parse(body);
                            record.isSetup = (body.isSetup=='1'); // TODO - obsolete?
                            record.isBusted = (body.isBusted=='1'); // TODO obsolete?
                            if(body.status) {
				//                          console.log("Body.status = " + body.status);
				record.isSetup = body.status.isSetup;
				if(body.status.isBusted) {
                                    record.isBusted = true;
                                    record.busted = body.status.isBusted;
				}
				record.users = body.status.users;
				record.guests = body.status.guests;
				record.mem  = body.status.memfree + '/' +  body.status.memtotal;
				record.info = body.status.phase + " " + body.status.newVersion + ' ' + body.status.environment;
				record.load = body.status.sysload + ' cpu='+body.status.sysprocs;
				record.dbused = body.status.dbused;
				record.uptime = body.status.uptime;  // string, unfortunately
				record.stamp  = new Date(body.status.surveyRunningStamp);
                            }
                        } catch(e) {
                            console.log ("Unable to parse: " + body + " - " + e.toString());
			    record.isSetup = false;
			    record.isBusted = true;
			    record.busted = record.statusCode + 'Fail: ' + body;
			    body = null;
                        }
                    }

                    postFetchStatus(record, body);
                };
            })(server,j));
        }
    }
    db.sync(function (err) {
        if(err) throw(err);
        //!err && console.log("db synced on update!");
    });
    return latestUpdate;
};

    exports._pollsec =  (CONFIG.watcher.polltime || 3600);
    exports._probsec =  (CONFIG.watcher.probation_time || 500);
    
    exports._interval = setInterval(exports.poll,exports._pollsec*1000);
    
    // get the last time for each server
    for(var k in servers) {
        (function(k){return function(){FetchStatus.find({server: k}, 1, ["when","Z"], function(err, stat) {
            if(err) throw (err);
            
            if(stat && stat.length >0) {
                var res = stat[0];
                var up = (res.isBusted==false && res.statusCode == 200);
                servers[k].lastKnownStatus={ when: res.when, up: up,
                                             probation: res.probation,
                                             id: res.id };
                if(VERBOSE) console.log("SERVER " + k + " : up="+up);
            } else {
                if(VERBOSE) console.log("Not found: " + k);
            }
        });};})(k)();
    }

    console.log("Polling every " + CONFIG.watcher.polltime + "s");
    exports.poll(); // first time

    // DB based now ready
    exports._latest = function(req, res){
        res.send(
            { 
                now: new Date().getTime(),
                hosts: hosts,
                servers: servers
            });
    };

    // DB based now ready
    exports._history = function(req, res){

        var server = req.query.server;
        var limit = 1024;
        //if(req.query.limit) {
        //limit = Integer.parse(req.query.limit);
        //}

        FetchStatus.find({server: server}, limit, ["when","Z"], function(err, stat) {
            if(err) throw (err);
            
            if(stat && stat.length >0) {
                res.send({ now: new Date().getTime(), server: server, data: stat });
            } else {
                res.send({ now: new Date().getTime(), server: server, err: 'not found' });
            }
        });
    };

});

// set up DB versions

}); // orm.connect



