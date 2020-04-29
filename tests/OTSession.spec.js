import React from 'react';
import { mount } from 'enzyme';
import toJson from 'enzyme-to-json';
import { Text } from 'react-native';

import OTSession from '../src/OTSession';
import { OT } from '../src/OT';

jest.mock('../src/OT', () => ({
  OT: {
    disconnectSession: jest.fn(),
    initSession: jest.fn(),
    connect: jest.fn()
  },
  setNativeEvents: jest.fn()
}));

describe('OTSession', () => {
  let apiKey, sessionId, token;

  beforeEach(() => {
    apiKey = 'fakeApiKey';
    sessionId = 'fakeSessionId';
    token = 'fakeToken';
  });

  describe('no props', () => {
    let sessionComponent;
    console.error = jest.fn();
    console.log = jest.fn();

    beforeEach(() => {
      console.error.mockClear();
      sessionComponent = mount(<OTSession />);
    });

    describe('missing credentials', () => {

      it('should render an empty view', () => {
        expect(toJson(sessionComponent)).toMatchSnapshot();
      });

      it('should call console error', () => {
        expect(console.error).toHaveBeenCalled();
        expect(console.error).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('with props and children', () => {
    let sessionComponent;

    beforeEach(() => {
      sessionComponent = mount(
        <OTSession apiKey={apiKey} sessionId={sessionId} token={token}>
          <Text />
          <Text />
        </OTSession>
      );
    });

    it('should have two children', () => {
      expect(toJson(sessionComponent)).toMatchSnapshot();
    });

    it('should call OT.initSession', () => {
      expect(OT.initSession).toHaveBeenCalled();
    });

    it('should call createSession when component mounts', () => {
      const instance = sessionComponent.instance();
      jest.spyOn(instance, 'createSession');
      instance.componentDidMount();
      
      expect(instance.createSession).toHaveBeenCalledTimes(1);
    });

    it('should call disconnectSession when component unmounts', () => {
      const instance = sessionComponent.instance();
      jest.spyOn(instance, 'disconnectSession');
      sessionComponent.unmount();

      expect(instance.disconnectSession).toHaveBeenCalled();
    });
  });
});