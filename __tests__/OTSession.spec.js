import React from 'react';
import renderer from 'react-test-renderer';
import { mount } from 'enzyme';
import toJson from 'enzyme-to-json';
import { Text } from 'react-native';

import OTSession from '../src/OTSession';

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
    describe('missing credentials', () => {
      let component;
      console.error = jest.fn();
      console.log = jest.fn();

      beforeEach(() => {
        console.error.mockClear();
        component = mount(<OTSession />);
      });

      it('should render an empty view', () => {
        expect(toJson(component)).toMatchSnapshot();
      });

      it('should call console error', () => {
        expect(console.error).toHaveBeenCalled();
        expect(console.error).toHaveBeenCalledTimes(1);
      });
    });
  });

  describe('with props', () => {
    describe('with children', () => {
      it('should have two children', () => {
        const sessionComponent = renderer.create(
          <OTSession apiKey={apiKey} sessionId={sessionId} token={token}>
            <Text />
            <Text />
          </OTSession>
        ).toJSON();
        expect(sessionComponent).toMatchSnapshot();
      });
    });
  });
});