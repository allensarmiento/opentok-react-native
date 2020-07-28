import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { View, Platform } from 'react-native';
import { isNull } from 'underscore';
import { checkAndroidPermissions, OT, removeNativeEvents, nativeEvents, setNativeEvents } from './OT';
import { sanitizeProperties, sanitizePublisherEvents } from './helpers/OTPublisherHelper';
import OTPublisherView from './views/OTPublisherView';
import { getOtrnErrorEventHandler } from './helpers/OTHelper';
import { isConnected } from './helpers/OTSessionHelper';
import OTContext from './contexts/OTContext';

const uuid = require('uuid/v4');

class OTPublisher extends Component {
  constructor(props, context) {
    super(props, context);
    this.state = {
      initError: null,
      publisher: null,
      publisherId: uuid(),
    };
    this.initComponent();
  }
  initComponent = () => {
    this.componentEvents = {
      sessionConnected: Platform.OS === 'android' ? 'session:onConnected' : 'session:sessionDidConnect',
    };
    this.componentEventsArray = Object.values(this.componentEvents);   
    this.otrnEventHandler = getOtrnErrorEventHandler(this.props.eventHandlers); 
    this.publisherEvents = sanitizePublisherEvents(this.state.publisherId, this.props.eventHandlers);
    setNativeEvents(this.publisherEvents);
    OT.session.setJSComponentEvents(this.componentEventsArray);
    if (this.context.sessionId) {
      this.sessionConnected = nativeEvents.addListener(`${this.context.sessionId}:${this.componentEvents.sessionConnected}`, () => this.sessionConnectedHandler());
    }
    console.log('Publisher component initialized');
  }
  componentDidMount() {
    this.createPublisher();
  }
  componentDidUpdate(previousProps) {
    const useDefault = (value, defaultValue) => (value === undefined ? defaultValue : value);
    const shouldUpdate = (key, defaultValue) => {
      const previous = useDefault(previousProps.properties[key], defaultValue);
      const current = useDefault(this.props.properties[key], defaultValue);
      return previous !== current;
    };

    const updatePublisherProperty = (key, defaultValue) => {
      if (shouldUpdate(key, defaultValue)) {
        const value = useDefault(this.props.properties[key], defaultValue);
        console.log(key);
        console.log(this.state.publisherId);
        console.log(value);
        if (key === 'cameraPosition') {
          OT.publisher.changeCameraPosition(this.state.publisherId, value);
        } else {
          OT.publisher[key](this.state.publisherId, value);          
        }
      }
    };

    updatePublisherProperty('publishAudio', true);
    updatePublisherProperty('publishVideo', true);
    updatePublisherProperty('cameraPosition', 'front');
  }
  componentWillUnmount() {
    OT.session.destroyPublisher(this.state.publisherId, (error) => {
      if (error) {
        this.otrnEventHandler(error);
      } else {
        this.sessionConnected.remove();
        OT.session.removeJSComponentEvents(this.componentEventsArray);
        removeNativeEvents(this.publisherEvents);
      }
    });
  }
  sessionConnectedHandler = () => {
    if (isNull(this.state.publisher) && isNull(this.state.initError)) {
      this.publish();
    }
  }
  createPublisher() {
    if (Platform.OS === 'android') {
      checkAndroidPermissions()
        .then(() => {
          this.initPublisher();
          console.log('Publisher created')
        })
        .catch((error) => {
          this.otrnEventHandler(error);
          console.log('Error creating publisher: ' + error);
        });
    } else {
      this.initPublisher();
    }
  }
  initPublisher() {
    const publisherProperties = sanitizeProperties(this.props.properties);
    OT.session.initPublisher(this.state.publisherId, publisherProperties, (initError) => {
      if (initError) {
        this.setState({ initError });
        this.otrnEventHandler(initError);
        console.log('Error initializing publisher: ' + initError);
      } else {
        if (this.context.sessionId) {
          // TODO: This method might need to be in the publisher
          OT.session.getSessionInfo(this.context.sessionId, (session) => {
            if (!isNull(session) && isNull(this.state.publisher) && isConnected(session.connectionStatus)) {
              // ! This does not get called, this.publish() is called in the sessionConnectedHandler
              console.log('Calling publish method');
              this.publish();
            } else {
              console.log('Error, not calling publish method');
              console.log('Session: ' + JSON.stringify(session));
              console.log('State publisher: ' + JSON.stringify(this.state.publisher));
              console.log('Connected: ' + session.connectionStatus);
            }
          });
        } else {
          console.log('There is no context session id');
        }
      }
    });
  }
  publish() {
    OT.session.publish(this.context.sessionId, this.state.publisherId, (publishError) => {
      if (publishError) {
        this.otrnEventHandler(publishError);
        console.log('Error publishing publisher: ' + publishError);
      } else {
        console.log('Publish successful');
        this.setState({ publisher: true });
      }
    });
  }
  render() {
    const { publisher, publisherId } = this.state;
    const { sessionId } = this.context;
    if (publisher && publisherId) {
      console.log('Rendering the publisher');
      return <OTPublisherView publisherId={publisherId} sessionId={sessionId} {...this.props} />;
    }
    console.log('No publisher found to render');
    return <View />;
  }
}
const viewPropTypes = View.propTypes;
OTPublisher.propTypes = {
  ...viewPropTypes,
  properties: PropTypes.object, // eslint-disable-line react/forbid-prop-types
  eventHandlers: PropTypes.object, // eslint-disable-line react/forbid-prop-types
};
OTPublisher.defaultProps = {
  properties: {},
  eventHandlers: {},
};
OTPublisher.contextType = OTContext;
export default OTPublisher;
